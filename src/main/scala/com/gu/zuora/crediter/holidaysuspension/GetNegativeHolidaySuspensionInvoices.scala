package com.gu.zuora.crediter.holidaysuspension
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

import com.gu.zuora.crediter.Models.{ExportCommand, NegativeInvoiceFileLine}
import com.gu.zuora.crediter.Types.SerialisedJson
import spray.json.{JsBoolean, JsObject, JsString}

case object GetNegativeHolidaySuspensionInvoices extends ExportCommand {

  override val getJSON: SerialisedJson = {
    val exportQuery = NegativeInvoiceFileLine.selectForZOQL +
      " WHERE InvoiceItem.ChargeName = 'Holiday Credit' and Invoice.Balance < 0 and Invoice.Status = 'Posted'"

    JsObject(
      "Format" -> JsString("csv"),
      "Name" -> JsString(s"Negative Holiday Suspension Invoices Export - ${now.format(ISO_DATE_TIME)}"),
      "Query" -> JsString(exportQuery),
      "Zip" -> JsBoolean(false)
    ).toString
  }

}