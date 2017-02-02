package com.gu.zuora.crediter.holidaysuspension

import com.gu.zuora.crediter.Models.{CreateCreditBalanceAdjustmentCommand, NegativeInvoiceToTransfer}
import com.gu.zuora.soap.CreditBalanceAdjustment

object CreateHolidaySuspensionCredit extends CreateCreditBalanceAdjustmentCommand {

  def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = {
    CreditBalanceAdjustment(
      Amount = Some(Some(invoice.transferrableBalance)),
      Comment = Some(Some(s"${this.getClass.getSimpleName} is transferring negative Holiday Suspension invoice ${invoice.invoiceNumber} into subscriber ${invoice.subscriberId}'s account credit.")),
      ReasonCode = Some(Some("Holiday Suspension Credit")),
      SourceTransactionNumber = Some(Some(invoice.invoiceNumber)),
      Type = Some(Some("Increase"))
    )
  }

}