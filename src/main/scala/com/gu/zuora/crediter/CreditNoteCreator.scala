package com.gu.zuora.crediter

import java.lang.System.getenv

import com.gu.zuora.crediter.InvoiceToTransfer.fromHolidayCreditInvoicesExportLine
import com.gu.zuora.crediter.NegativeHolidayCreditInvoicesExportDownloader.{ExportFile, ExportLine}
import com.gu.zuora.crediter.ZuoraClients.zuoraSoapClient
import com.gu.zuora.soap.CreditBalanceAdjustment
import com.typesafe.scalalogging.LazyLogging

case class InvoiceToTransfer(id: String, invoiceNumber: String, balance: BigDecimal, subscriberId: String)
object InvoiceToTransfer {
  def fromHolidayCreditInvoicesExportLine(reportLine: ExportLine): Either[String, InvoiceToTransfer] = {
    val invoiceBalance = BigDecimal(reportLine.invoiceBalance).setScale(2, BigDecimal.RoundingMode.HALF_DOWN) // Not HALF_EVEN - we always round to the customer's benefit
    if (invoiceBalance < 0) {
      Right(InvoiceToTransfer(reportLine.invoiceId, reportLine.invoiceNumber, invoiceBalance, reportLine.subscriptionName))
    } else {
      Left(s"Ignored invoice ${reportLine.invoiceNumber} with balance ${reportLine.invoiceBalance} for subscription: ${reportLine.subscriptionName} as its balance is not negative")
    }
  }
}

object CreditNoteCreator extends LazyLogging {

  private val REASON_CODE = "Holiday Suspension Credit"

  def fromExportFile(exportFileId: String): Boolean = {
    val maybeReportCSV = NegativeHolidayCreditInvoicesExportDownloader.downloadGeneratedExportFile(exportFileId)
    val invoicesWhichNeedCrediting = maybeReportCSV.map(invoicesFromReport).getOrElse {
      logger.error("Unable to download report of negative invoices to credit")
      Set.empty[InvoiceToTransfer]
    }
    if (invoicesWhichNeedCrediting.isEmpty) {
      logger.warn("No negative Holiday Credit invoices reqire crediting today")
    }
    makeCreditAdjustments(invoicesWhichNeedCrediting)
    true
  }

  def invoicesFromReport(report: ExportFile): Set[InvoiceToTransfer] = {
    report.reportLines.flatMap { reportLine =>
      val result = fromHolidayCreditInvoicesExportLine(reportLine)
      result.left.foreach(x => logger.warn(x))
      result.right.toOption
    }.toSet
  }

  def makeCreditAdjustments(invoices: Set[InvoiceToTransfer]): Unit = {
    // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
    val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
    if (invoicesToCredit.nonEmpty) {
      val loginResponse = zuoraSoapClient.login(Some(getenv("ZuoraApiAccessKeyId")), Some(getenv("ZuoraApiSecretAccessKey")))
      if (loginResponse.isLeft) {
        logger.error(s"Was unable to log in to Zuora to create credit adjustments for invoices: ${invoicesToCredit.map(_.invoiceNumber).mkString(", ")}. Reason: " + loginResponse.left.toOption.mkString)
      } else {
        println("HELLO")
        println(loginResponse)
        val adjustments = invoicesToCredit.map(createCreditAdjustment)
        println(adjustments)
        //adjustments.foreach(zuoraSoapClient.create)
      }
    }
  }

  def createCreditAdjustment(invoice: InvoiceToTransfer): CreditBalanceAdjustment = {
    CreditBalanceAdjustment(
      Amount = Some(Some(invoice.balance)),
      Comment = Some(Some(s"${this.getClass.getSimpleName} is converting negative Holiday Credit invoice ${invoice.invoiceNumber} into subscriber ${invoice.subscriberId}'s account credit.")),
      ReasonCode = Some(Some(REASON_CODE)),
      SourceTransactionNumber = Some(Some(invoice.invoiceNumber)),
      Type = Some(Some("Increase"))
    )
  }
}