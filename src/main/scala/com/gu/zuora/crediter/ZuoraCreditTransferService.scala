package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.{CreateCreditBalanceAdjustmentCommand, ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.crediter.Types.{CreditBalanceAdjustmentIDs, ErrorMessage, ExportId, NegativeInvoiceCSVFile}
import com.gu.zuora.soap.{CallOptions, Create, CreditBalanceAdjustment, SessionHeader}
import com.typesafe.scalalogging.LazyLogging


class ZuoraCreditTransferService(command: CreateCreditBalanceAdjustmentCommand)(implicit zuoraClients: ZuoraClients) extends LazyLogging {

  import ModelReaders._
  private val soapCallOptions = CallOptions(useSingleTransaction = Some(Some(false)))
  private val zuoraExportDownloader = new ZuoraExportDownloader

  def processExportFile(exportId: ExportId): Int = {
    val maybeExportCSV = zuoraExportDownloader.downloadGeneratedExportFile(exportId)
    val invoicesWhichNeedCrediting = maybeExportCSV.map(x => invoicesFromReport(ExportFile(x))).getOrElse {
      logger.error("Unable to download export of negative invoices to credit")
      Set.empty[NegativeInvoiceToTransfer]
    }
    if (invoicesWhichNeedCrediting.isEmpty) {
      logger.warn("No negative invoices reqire crediting today")
    }
    makeCreditAdjustments(invoicesWhichNeedCrediting)
  }

  def invoicesFromReport(report: NegativeInvoiceCSVFile): Set[NegativeInvoiceToTransfer] = {
    report.reportLines.flatMap { reportLine =>
      val result = processNegativeInvoicesExportLine(reportLine)
      result.left.foreach(x => logger.warn(x))
      result.right.toOption
    }.toSet
  }

  def processNegativeInvoicesExportLine(reportLine: NegativeInvoiceFileLine): Either[ErrorMessage, NegativeInvoiceToTransfer] = {
    val invoiceBalance = BigDecimal.apply(reportLine.invoiceBalance).setScale(2, BigDecimal.RoundingMode.HALF_DOWN) // Not HALF_EVEN - we always round to the customer's benefit
    if (invoiceBalance < 0) {
      Right(NegativeInvoiceToTransfer(reportLine.invoiceNumber, invoiceBalance, reportLine.subscriptionName))
    } else {
      Left(s"Ignored invoice ${reportLine.invoiceNumber} with balance ${reportLine.invoiceBalance} for subscription: ${reportLine.subscriptionName} as its balance is not negative")
    }
  }


  def makeCreditAdjustments(invoices: Set[NegativeInvoiceToTransfer]): Int = {
    // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
    val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
    if (invoicesToCredit.isEmpty) 0 else {
      val allInvoiceNumbers = invoicesToCredit.map(a => s"${a.invoiceNumber} (${a.invoiceBalance})").mkString(", ")
      zuoraClients.getSoapAPISession.fold(0) { implicit sessionHeader =>
        logger.info(s"Attempting to create ${allInvoiceNumbers.length} Credit Balance Adjustments for Invoices: $allInvoiceNumbers")
        val adjustmentsToMake = invoicesToCredit.map(command.createCreditBalanceAdjustment)
        val createdAdjustments = createCreditBalanceAdjustments(adjustmentsToMake)
        logger.info(s"Successfully created ${createdAdjustments.length} Credit Balance Adjustments with IDs: ${createdAdjustments.mkString(", ")}")
        createdAdjustments.length
      }
    }
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
      saveResult.Id.flatten
    }).flatten
  }

}
