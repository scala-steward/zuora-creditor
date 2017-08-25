package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.{CreateCreditBalanceAdjustmentCommand, NegativeInvoiceToTransfer}
import com.gu.zuora.soap.CreditBalanceAdjustment
import purecsv.safe._

import scala.math.BigDecimal.RoundingMode.UP
import scala.util.Try


object VoucherPriceRise extends App {

  private implicit val zuoraClients = ZuoraAPIClientsFromEnvironment

  case class VoucherInvoiceLine(invoiceNumber: String, invoiceAmount: Double, subscriptionName: String)

  def convertInvoiceLineToNegativeInvoiceToTransfer(invoiceLine: VoucherInvoiceLine): Option[NegativeInvoiceToTransfer] = {
    val invoiceBalance = Try(BigDecimal.apply(invoiceLine.invoiceAmount).setScale(2, UP)).toOption
    invoiceBalance.map(amount => NegativeInvoiceToTransfer(invoiceLine.invoiceNumber, amount, invoiceLine.subscriptionName))
  }

  object CreateVoucherRefund extends CreateCreditBalanceAdjustmentCommand {

    def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = {
      CreditBalanceAdjustment(
        Amount = Some(Some(invoice.transferrableBalance)),
        Comment = Some(Some(s"Customer Service Adjustment - Voucher invoice: ${invoice.invoiceNumber} into subscriber ${invoice.subscriberId}'s account credit.")),
        ReasonCode = Some(Some("Customer Satisfaction")),
        SourceTransactionNumber = Some(Some(invoice.invoiceNumber)),
        Type = Some(Some("Increase"))
      )
    }

  }
  val service = new CreditTransferService(CreateVoucherRefund)

  val negativeInvoiceCSVReader: CSVReader[VoucherInvoiceLine] = CSVReader[VoucherInvoiceLine]
  val voucherInvoiceLines = negativeInvoiceCSVReader.readCSVFromFileName("voucher-refunds.csv", skipHeader = true).flatMap(_.toOption).toSet
  val negativeInvoicesToTransfer = voucherInvoiceLines.flatMap(convertInvoiceLineToNegativeInvoiceToTransfer)
  val creditBalanceAdjustmnetIds = service.makeCreditAdjustments(negativeInvoicesToTransfer)

}
