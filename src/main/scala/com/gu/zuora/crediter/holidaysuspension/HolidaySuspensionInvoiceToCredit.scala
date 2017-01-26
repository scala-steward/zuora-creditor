package com.gu.zuora.crediter.holidaysuspension

import java.lang.System.getenv

import com.gu.zuora.crediter.ModelReaders._
import com.gu.zuora.crediter.Models.{ExportFile, InvoiceToTransfer, NegativeInvoiceFileLine}
import com.gu.zuora.crediter.Types._
import com.gu.zuora.crediter.{ZuoraClients, ZuoraExportDownloader}
import com.gu.zuora.soap.{CallOptions, Create, CreditBalanceAdjustment, SessionHeader}
import com.typesafe.scalalogging.LazyLogging

class HolidaySuspensionInvoiceToCredit(implicit zuoraClients: ZuoraClients) extends LazyLogging {

  private val soapCallOptions = CallOptions(useSingleTransaction = Some(Some(false)))
  private val reasonCode = "Holiday Suspension Credit"
  private val zuoraExportDownloader = new ZuoraExportDownloader

  def fromExportFile(exportId: ExportId): Int = {
    val maybeExportCSV = zuoraExportDownloader.downloadGeneratedExportFile(exportId)
    val invoicesWhichNeedCrediting = maybeExportCSV.map(x => invoicesFromReport(ExportFile(x))).getOrElse {
      logger.error("Unable to download export of negative invoices to credit")
      Set.empty[InvoiceToTransfer]
    }
    if (invoicesWhichNeedCrediting.isEmpty) {
      logger.warn("No negative Holiday Credit invoices reqire crediting today")
    }
    makeCreditAdjustments(invoicesWhichNeedCrediting)
  }

  def invoicesFromReport(report: NegativeInvoiceCSVFile): Set[InvoiceToTransfer] = {
    report.reportLines.flatMap { reportLine =>
      val result = processHolidayCreditInvoicesExportLine(reportLine)
      result.left.foreach(x => logger.warn(x))
      result.right.toOption
    }.toSet
  }

  def processHolidayCreditInvoicesExportLine(reportLine: NegativeInvoiceFileLine): Either[ErrorMessage, InvoiceToTransfer] = {
    val invoiceBalance = BigDecimal.apply(reportLine.invoiceBalance).setScale(2, BigDecimal.RoundingMode.HALF_DOWN) // Not HALF_EVEN - we always round to the customer's benefit
    if (invoiceBalance < 0) {
      Right(InvoiceToTransfer(reportLine.invoiceNumber, invoiceBalance, reportLine.subscriptionName))
    } else {
      Left(s"Ignored invoice ${reportLine.invoiceNumber} with balance ${reportLine.invoiceBalance} for subscription: ${reportLine.subscriptionName} as its balance is not negative")
    }
  }

  def logInToSoapAPI(): Option[SessionHeader] = {
    val loginResponse = zuoraClients.zuoraSoapClient.login(Some(getenv("ZuoraApiAccessKeyId")), Some(getenv("ZuoraApiSecretAccessKey")))
    if (loginResponse.isLeft) {
      logger.error(s"Unable to log in to Zuora API. Reason: " + loginResponse.left.toOption.mkString)
    }
    for {
      response <- loginResponse.right.toOption
      result <- response.result
      sessionId <- result.Session
    } yield {
      SessionHeader(sessionId)
    }
  }

  def makeCreditAdjustments(invoices: Set[InvoiceToTransfer]): Int = {
    // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
    val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
    if (invoicesToCredit.isEmpty) 0 else {
      val allInvoiceNumbers = invoicesToCredit.map(a => s"${a.invoiceNumber} (${a.balance})").mkString(", ")
      logInToSoapAPI().fold(0) { implicit sessionHeader =>
        logger.info(s"Attempting to create ${allInvoiceNumbers.length} Credit Balance Adjustments for Invoices: $allInvoiceNumbers")
        val adjustmentsToMake = invoicesToCredit.map(createCreditAdjustment)
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


  def createCreditAdjustment(invoice: InvoiceToTransfer): CreditBalanceAdjustment = {
    CreditBalanceAdjustment(
      Amount = Some(Some(invoice.balance * -1)),
      Comment = Some(Some(s"${this.getClass.getSimpleName} is transferring negative Holiday Credit invoice ${invoice.invoiceNumber} into subscriber ${invoice.subscriberId}'s account credit.")),
      ReasonCode = Some(Some(reasonCode)),
      SourceTransactionNumber = Some(Some(invoice.invoiceNumber)),
      Type = Some(Some("Increase"))
    )
  }
}