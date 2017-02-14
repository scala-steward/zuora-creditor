package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.{CreateCreditBalanceAdjustmentCommand, ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.Types.{CreditBalanceAdjustmentIDs, ErrorMessage, ExportId, NegativeInvoiceReport}
import com.gu.zuora.soap.CreditBalanceAdjustment

import scala.math.BigDecimal.RoundingMode.UP
import scala.util.Try


class CreditTransferService(command: CreateCreditBalanceAdjustmentCommand)(implicit zuoraClients: ZuoraAPIClients) extends Logging {

  import ModelReaders._
  private implicit val zuoraRestClient = zuoraClients.zuoraRestClient
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
    // Not HALF_EVEN rounding - we always round to the customer's benefit using UP
    val invoiceBalance = Try(BigDecimal.apply(reportLine.invoiceBalance).setScale(2, UP)).toOption
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
    if (invoices.nonEmpty) {
      // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
      val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
      val allInvoiceNumbers = invoicesToCredit.map(a => s"${a.invoiceNumber} (${a.invoiceBalance})").mkString(", ")
      logger.info(s"Creating ${invoicesToCredit.length} Credit Balance Adjustments for Invoices: $allInvoiceNumbers")

      val adjustmentsToMake = invoicesToCredit.map(command.createCreditBalanceAdjustment)
      val createdAdjustments = createCreditBalanceAdjustments(adjustmentsToMake)

      logger.info(s"Successfully created ${createdAdjustments.size} Credit Balance Adjustments with IDs: ${createdAdjustments.mkString(", ")}")
      createdAdjustments
    } else {
      Seq.empty
    }
  }

  def createCreditBalanceAdjustments(adjustmentsToMake: Seq[CreditBalanceAdjustment]): CreditBalanceAdjustmentIDs = {
    val soapClient = zuoraClients.zuoraSoapClient
    val batches = adjustmentsToMake.grouped(soapClient.maxNumberOfCreateObjects).toList // ensures eagerness
    batches.flatMap { adjustments =>
      logger.info(s"Attempting to create ${adjustments.length} Credit Balance Adjustments for Invoices: ${adjustments.flatMap(_.SourceTransactionNumber).mkString(", ")}")
      val createResponse = soapClient.create(adjustments)
      if (createResponse.isLeft) {
        logger.error(s"Unable to create any Credit Balance Adjustments. Reason: ${createResponse.left.get}")
      }
      (for {
        responseOpt <- createResponse.right.toSeq
        saveResult <- responseOpt.result
      } yield {
        for (error <- saveResult.Errors.flatten) {
          logger.error(s"Error creating Credit Balance Adjustment: ${error.Message.flatten.mkString}")
        }
        val maybeId = saveResult.Id.flatten
        maybeId.foreach(id => logger.info(s"Successfully created Credit Balance Adjustment, ID: $id"))
        maybeId
      }).flatten
    }
  }

}
