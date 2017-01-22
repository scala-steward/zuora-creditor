package com.gu.zuora.crediter

import com.gu.zuora.crediter.ZuoraClients.zuoraRestClient
import com.typesafe.scalalogging.LazyLogging
import purecsv.unsafe.CSVReader
import spray.json.{DefaultJsonProtocol => DJP, _}

object NegativeHolidayCreditInvoicesExportDownloader extends LazyLogging {
  import DJP._

  case class ExportLine(subscriptionName: String, invoiceId: String, invoiceNumber: String, invoiceDate: String, invoiceBalance: String)

  case class ExportFile(rawCSV: String) {
    val reportLines: Seq[ExportLine] = {
      val allLines = CSVReader[ExportLine].readCSVFromString(rawCSV) // includes header row!
      allLines.tail
    }
  }

  def downloadGeneratedExportFile(exportId: String): Option[ExportFile] = {
    val exportResponse = zuoraRestClient(s"object/export/$exportId").asString
    val maybeFileId = exportResponse.body.parseJson.asJsObject.getFields("FileId").headOption.map(_.convertTo[String]).filter(_.nonEmpty)
    if (maybeFileId.isEmpty) {
      logger.error(s"No FileId for Export: $exportId. Zuora response: $exportResponse")
    }
    maybeFileId.map(fileId => ExportFile(zuoraRestClient(s"file/$fileId").asString.body))
  }
}

