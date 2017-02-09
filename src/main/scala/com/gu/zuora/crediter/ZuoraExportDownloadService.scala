package com.gu.zuora.crediter

import com.gu.zuora.crediter.Types.{ExportId, FileId, RawCSVText, SerialisedJson}
import play.api.libs.json.Json.parse

import scala.util.Try

class ZuoraExportDownloadService(implicit zuoraRestClient: ZuoraRestClient) extends Logging {

  def downloadGeneratedExportFile(exportId: ExportId): Option[RawCSVText] = {
    Option(exportId).filter(_.nonEmpty).flatMap { validExportId =>
      val exportResponse = getZuoraExport(validExportId)
      val maybeFileId = extractFileId(exportResponse)
      if (maybeFileId.isEmpty) {
        logger.error(s"No FileId found in Zuora Export: $validExportId. Zuora response: $exportResponse")
      }
      maybeFileId.map(downloadExportFile).filter(_.nonEmpty)
    }
  }

  private def getZuoraExport(exportId: ExportId) = zuoraRestClient.makeRestGET(s"object/export/$exportId")

  private def extractFileId(response: SerialisedJson) =
    Try((parse(response) \ "FileId").asOpt[String]).toOption.flatten.filter(_.nonEmpty)

  private def downloadExportFile(fileId: FileId) = zuoraRestClient.downloadFile(s"file/$fileId")
}

