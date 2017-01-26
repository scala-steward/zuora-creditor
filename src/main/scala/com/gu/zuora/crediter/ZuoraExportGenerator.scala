package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.ExportCommand
import com.gu.zuora.crediter.Types.{ExportId, SerialisedJson, ZuoraRestClient}
import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol => DJP, _}

class ZuoraExportGenerator(command: ExportCommand)(implicit zuoraClients: ZuoraClients) extends LazyLogging {

  import DJP._

  def generate(): Option[ExportId] = {
    val maybeExportId = scheduleExportInZuora()
    if (maybeExportId.isEmpty) {
      logger.error("No Zuora Export created")
    } else {
      logger.info(s"Successfully created Zuora Export: ${maybeExportId.mkString}")
    }
    maybeExportId
  }

  def scheduleExportInZuora(): Option[ExportId] = {
    val commandJSON = command.getJSON
    val response = zuoraClients.zuoraRestClient("object/export").postData(commandJSON).asString
    if (!response.isSuccess) {
      logger.error(s"Request to create Zuora Export using command: $commandJSON")
    }
    extractExportId(response.body).filter(_.nonEmpty)
  }

  def extractExportId(response: SerialisedJson): Option[ExportId] =
    response.parseJson.asJsObject.getFields("Id").headOption.map(_.convertTo[ExportId])
}