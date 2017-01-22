package com.gu.zuora.crediter

import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol => DJP, _}

import scalaj.http.HttpResponse

object NegativeHolidayCreditInvoicesExportGenerator extends LazyLogging {
  import DJP._

  private val exportQuery =
    "SELECT Subscription.Name, Invoice.Id, Invoice.InvoiceNumber, Invoice.InvoiceDate, Invoice.Balance " +
      "FROM InvoiceItem " +
      "WHERE InvoiceItem.ChargeName = 'Holiday Credit' and Invoice.Balance < 0 and Invoice.Status = 'Posted'"

  private val reportName = "Negative Holiday Credit Invoices Export"

  def generate(): Boolean = {
    val response = scheduleReportInZuora()
    val maybeExportId = extractExportId(response).filter(_.nonEmpty)
    maybeExportId.map(setCloudWatchAlarmToDownloadLater).getOrElse {
      logger.error(s"Unable to schedule $reportName as no Export ID. Zuora response: $response")
      false
    }
  }

  def scheduleReportInZuora(): HttpResponse[String] = {
    val response =
      ZuoraClients.zuoraRestClient("object/export")
        .postData(JsObject(
          "Format" -> JsString("csv"),
          "Name" -> JsString(s"$reportName - ${now.format(DateTimeFormatter.ISO_DATE_TIME)}"),
          "Query" -> JsString(exportQuery),
          "Zip" -> JsBoolean(false)
        ).toString)
        .asString
    println(response)
    response
  }

  def extractExportId(response: HttpResponse[String]): Option[String] = {
    if (response.isSuccess) {
      response.body.parseJson.asJsObject.getFields("Id").headOption.map(_.convertTo[String])
    } else {
      None
    }
  }

  def setCloudWatchAlarmToDownloadLater(exportId: String): Boolean = {
    logger.info(s"Setting CloudWatch alarm to fire in 1 minute's time with name/description: $exportId")
    true
  }
}