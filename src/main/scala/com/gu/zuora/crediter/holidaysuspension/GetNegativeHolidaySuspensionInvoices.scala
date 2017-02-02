package com.gu.zuora.crediter.holidaysuspension
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

import com.gu.zuora.crediter.Models.ExportCommand
import com.gu.zuora.crediter.Models.NegativeInvoiceFileLine.selectForZOQL
import com.gu.zuora.crediter.Types.SerialisedJson
import spray.json.{JsBoolean, JsObject, JsString}

case object GetNegativeHolidaySuspensionInvoices extends ExportCommand {

  override val getJSON: SerialisedJson = {
    JsObject(
      "Format" -> JsString("csv"),
      "Name" -> JsString(s"Negative Holiday Suspension Invoices Export - ${now.format(ISO_DATE_TIME)}"),
      "Query" -> JsString(s"$selectForZOQL AND InvoiceItem.ChargeName = 'Holiday Credit'"),
      "Zip" -> JsBoolean(false)
    ).toString
  }

}