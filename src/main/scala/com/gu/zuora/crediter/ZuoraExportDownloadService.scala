package com.gu.zuora.crediter

import com.gu.zuora.crediter.Types.{ExportId, FileId, RawCSVText, SerialisedJson}
import spray.json.{DefaultJsonProtocol => DJP, _}

class ZuoraExportDownloadService(implicit zuoraRestClient: ZuoraRestClient) extends Logging {
  import DJP._

  def downloadGeneratedExportFile(exportId: ExportId): Option[RawCSVText] = {
    val exportResponse = getZuoraExport(exportId)
    val maybeFileId = extractFileId(exportResponse)
    if (maybeFileId.isEmpty) {
      logger.error(s"No FileId found in Zuora Export: $exportId. Zuora response: $exportResponse")
    }
    maybeFileId.map(downloadExportFile).filter(_.nonEmpty)
  }

  private def getZuoraExport(exportId: ExportId) = zuoraRestClient.makeRestGET(s"object/export/$exportId")

  private def extractFileId(response: SerialisedJson) =
    response.parseJson.asJsObject.getFields("FileId").headOption.map(_.convertTo[FileId]).filter(_.nonEmpty)

  private def downloadExportFile(fileId: FileId) = zuoraRestClient.downloadFile(s"file/$fileId")
}

