package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.{CreateCreditBalanceAdjustmentCommand, ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.crediter.Types.{CreditBalanceAdjustmentIDs, ErrorMessage, ExportId, NegativeInvoiceReport}
import com.gu.zuora.soap.{CallOptions, Create, CreditBalanceAdjustment, SessionHeader}

import scala.util.Try


class CreditTransferService(command: CreateCreditBalanceAdjustmentCommand)(implicit zuoraClients: ZuoraAPIClients) extends Logging {

  import ModelReaders._
  private implicit val zuoraRestClient = zuoraClients.zuoraRestClient

  private val soapCallOptions = CallOptions(useSingleTransaction = Some(Some(false)))
  private val zuoraExportDownloader = new ZuoraExportDownloadService

  def processExportFile(exportId: ExportId): Int = {
    val maybeExportCSV = zuoraExportDownloader.downloadGeneratedExportFile(exportId)
    val invoicesWhichNeedCrediting = maybeExportCSV.map(x => invoicesFromReport(ExportFile(x))).getOrElse {
      logger.error("Unable to download export of negative invoices to credit")
      Set.empty[NegativeInvoiceToTransfer]
    }
    if (invoicesWhichNeedCrediting.isEmpty) {
      logger.warn("No negative invoices reqire crediting today")
    }
    makeCreditAdjustments(invoicesWhichNeedCrediting).size
  }

  def invoicesFromReport(report: NegativeInvoiceReport): Set[NegativeInvoiceToTransfer] = {
    report.reportLines.flatMap { reportLine =>
      val result = processNegativeInvoicesExportLine(reportLine)
      result.left.foreach(x => logger.warn(x))
      result.right.toOption
    }.toSet
  }

  def processNegativeInvoicesExportLine(reportLine: NegativeInvoiceFileLine): Either[ErrorMessage, NegativeInvoiceToTransfer] = {
    // Not HALF_EVEN rounding - we always round to the customer's benefit
    val invoiceBalance = Try(BigDecimal.apply(reportLine.invoiceBalance).setScale(2, BigDecimal.RoundingMode.UP)).toOption
    val invoiceNumberIsValid = reportLine.invoiceNumber.nonEmpty
    val subscriptionNameIsValid = reportLine.subscriptionName.nonEmpty

    if (invoiceBalance.exists(_ < 0) && invoiceNumberIsValid && subscriptionNameIsValid) {
      Right(NegativeInvoiceToTransfer(reportLine.invoiceNumber, invoiceBalance.get, reportLine.subscriptionName))
    } else {
      Left(s"Ignored invoice ${reportLine.invoiceNumber} dated ${reportLine.invoiceDate} with balance ${reportLine.invoiceBalance} for subscription: " +
        s"${reportLine.subscriptionName} as its balance is not negative or some other piece of information is missing")
    }
  }

  def makeCreditAdjustments(invoices: Set[NegativeInvoiceToTransfer]): CreditBalanceAdjustmentIDs = {
    // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
    val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
    val allInvoiceNumbers = invoicesToCredit.map(a => s"${a.invoiceNumber} (${a.invoiceBalance})").mkString(", ")
    logger.info(s"Attempting to create ${invoicesToCredit.length} Credit Balance Adjustments for Invoices: $allInvoiceNumbers")

    val adjustmentsToMake = invoicesToCredit.map(command.createCreditBalanceAdjustment)
    val createdAdjustments: CreditBalanceAdjustmentIDs = (for {
      _ <- adjustmentsToMake.headOption
      session <- zuoraClients.getSoapAPISession
    } yield createCreditBalanceAdjustments(adjustmentsToMake)(session)).getOrElse(Seq.empty[String])

    logger.info(s"Successfully created ${createdAdjustments.length} Credit Balance Adjustments with IDs: ${createdAdjustments.mkString(", ")}")
    createdAdjustments
  }

  def createCreditBalanceAdjustments(adjustments: Seq[CreditBalanceAdjustment])(implicit sessionHeader: SessionHeader): CreditBalanceAdjustmentIDs = {
    val createResponse = zuoraClients.zuoraSoapClient.create(Create(adjustments), soapCallOptions, sessionHeader)
    if (createResponse.isLeft) {
      logger.error(s"Unable to create any Credit Balance Adjustments. Reason: " + createResponse.left.toOption.mkString)
    }
    (for {
      responseOpt <- createResponse.right.toSeq
      saveResult <- responseOpt.result
    } yield {
      for (error <- saveResult.Errors.flatten) {
        logger.warn(s"Error creating Credit Balance Adjustment: ${error.Message.flatten.mkString}")
      }
      saveResult.Id.flatMap(id => {
        logger.info(s"Successfully created Credit Balance Adjustment, ID: ${id.mkString}")
        id
      })
    }).flatten
  }

}
