package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.ExportCommand
import com.gu.zuora.creditor.Types.{ExportId, SerialisedJson}
import play.api.libs.json.Json.parse

import scala.util.Try

class ZuoraExportGenerator(command: ExportCommand)(implicit zuoraRestClient: ZuoraRestClient) extends Logging {
  
  def generate(): Option[ExportId] = {
    val maybeExportId = scheduleExportInZuora()
    if (maybeExportId.isEmpty) {
      logger.error("No Zuora Export created")
    } else {
      logger.info(s"Successfully created Zuora Export: ${maybeExportId.mkString}")
    }
    maybeExportId
  }

  private def scheduleExportInZuora(): Option[ExportId] = {
    val commandJSON = command.getJSON
    val response = zuoraRestClient.makeRestPOST("object/export")(commandJSON)
    if (response.isEmpty) {
      logger.error(s"Request to create Zuora Export using command: $commandJSON yielded no response")
    }
    extractExportId(response)
  }

  def extractExportId(response: SerialisedJson): Option[ExportId] = {
    Try((parse(response) \ "Id").asOpt[String]).toOption.flatten.filter(_.nonEmpty)
  }
}