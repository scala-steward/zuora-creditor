package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.NegativeInvoiceFileLine
import com.gu.zuora.crediter.Types.SerialisedJson
import com.gu.zuora.soap.CreditBalanceAdjustment
import purecsv.unsafe
import purecsv.unsafe.CSVReader

object Models {

  trait ExportFileLine
  case class NegativeInvoiceFileLine(subscriptionName: String, invoiceId: String, invoiceNumber: String, invoiceDate: String, invoiceBalance: String) extends ExportFileLine
  case object NegativeInvoiceFileLine {
    val selectForZOQL = "SELECT Subscription.Name, Invoice.InvoiceNumber, Invoice.InvoiceDate, Invoice.Balance FROM InvoiceItem"
  }
  case class NegativeInvoiceToTransfer(invoiceNumber: String, invoiceBalance: BigDecimal, subscriberId: String) {
    val transferrableBalance: BigDecimal = if (invoiceBalance < 0) invoiceBalance * -1 else 0
  }
  trait CreateCreditBalanceAdjustmentCommand {
    def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment
  }

  trait ExportCommand {
    def getJSON: SerialisedJson
  }

  case class ExportFile[S <: ExportFileLine](rawCSV: String)(implicit reader: CSVReader[S]) {
    val reportLines: Seq[S] = {
      val allLines = reader.readCSVFromString(rawCSV) // includes header row!
      allLines.tail
    }
  }
}

object ModelReaders {
  implicit val negativeInvoiceCSVReader: CSVReader[NegativeInvoiceFileLine] = unsafe.CSVReader[NegativeInvoiceFileLine]
}