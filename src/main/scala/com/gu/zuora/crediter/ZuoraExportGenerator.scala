package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.ExportCommand
import com.gu.zuora.crediter.Types.{ExportId, SerialisedJson}
import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol => DJP, _}

import scala.util.Try

class ZuoraExportGenerator(command: ExportCommand)(implicit zuoraRestClient: ZuoraRestClient) extends LazyLogging {
  
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
    if (!response.isEmpty) {
      logger.error(s"Request to create Zuora Export using command: $commandJSON")
    }
    extractExportId(response)
  }

  def extractExportId(response: SerialisedJson): Option[ExportId] = {
    import DJP._

    Try {
      response.parseJson.asJsObject.getFields("Id").headOption.map(_.convertTo[ExportId])
    }.toOption.flatten.filter(_.nonEmpty)
  }
}