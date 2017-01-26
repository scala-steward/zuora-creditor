package com.gu.zuora.crediter

import com.gu.zuora.crediter.Types.{ExportId, FileId, SerialisedJson}
import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol => DJP, _}

class ZuoraExportDownloader(implicit zuoraClients: ZuoraClients) extends LazyLogging {
  import DJP._

  def downloadGeneratedExportFile(exportId: ExportId): Option[String] = {
    val exportResponse = getZuoraExport(exportId)
    val maybeFileId = extractFileId(exportResponse)
    if (maybeFileId.isEmpty) {
      logger.error(s"No FileId found in Zuora Export: $exportId. Zuora response: $exportResponse")
    }
    maybeFileId.map(downloadExportFile)
  }

  private def getZuoraExport(exportId: ExportId) = zuoraClients.zuoraRestClient(s"object/export/$exportId").asString.body

  private def extractFileId(response: SerialisedJson) =
    response.parseJson.asJsObject.getFields("FileId").headOption.map(_.convertTo[FileId]).filter(_.nonEmpty)

  private def downloadExportFile(fileId: FileId) = zuoraClients.zuoraRestClient(s"file/$fileId").asString.body
}

