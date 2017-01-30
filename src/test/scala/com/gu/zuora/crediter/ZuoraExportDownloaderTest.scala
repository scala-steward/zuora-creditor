package com.gu.zuora.crediter

import com.gu.zuora.crediter.Types.SerialisedJson
import org.scalatest.FlatSpec

class ZuoraExportDownloaderTest extends FlatSpec {

  val exportId = "foo"
  val fileId = "bar"

  implicit val zuoraRestClient = new ZuoraRestClient {
    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = ""

    override def downloadFile(path: String): SerialisedJson = {
      assert(path ==  s"file/$fileId")
      "abc,123,abc,123"
    }
    override def makeRestGET(path: String): SerialisedJson = {
      assert(path == s"object/export/$exportId")
        """{"FileId":"bar"}"""
    }
  }

  behavior of "ZuoraExportDownloaderTest"

  it should "downloadGeneratedExportFile" in {
    val zuoraExportDownloader = new ZuoraExportDownloader
    zuoraExportDownloader.downloadGeneratedExportFile(exportId)
  }

}
