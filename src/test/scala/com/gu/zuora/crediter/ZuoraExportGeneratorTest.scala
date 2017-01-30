package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.ExportCommand
import com.gu.zuora.crediter.Types.SerialisedJson
import org.scalatest.FlatSpec

class ZuoraExportGeneratorTest extends FlatSpec {

  implicit val zuoraRestClient = new ZuoraRestClient {
    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = {
      assert(path == "object/export")
      assert(commandJSON == """{"export":"command"}""")
      """{"Id":"baz"}"""
    }
    override def makeRestGET(path: String) = ""
    override def downloadFile(path: String) = ""
  }

  val command = new ExportCommand {
    override def getJSON: SerialisedJson = """{"export":"command"}"""
  }

  behavior of "ZuoraExportGeneratorTest"

  it should "extractExportId" in {
    val generator = new ZuoraExportGenerator(command)
    assert(generator.extractExportId("""{"foo":"bar"}""").isEmpty)
    assert(generator.extractExportId("""{"Id":""}""").isEmpty)
    assert(generator.extractExportId("""{"Id": 1}""").isEmpty)
    assert(generator.extractExportId("""{Id: "bar"}""").isEmpty)
    assert(generator.extractExportId("""{"Id":"bar"}""").contains("bar"))
  }

  it should "generate" in {
    val generator = new ZuoraExportGenerator(command)
    assert(generator.generate().contains("baz"))
  }


}
