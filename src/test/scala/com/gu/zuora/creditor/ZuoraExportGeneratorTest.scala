package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.ExportCommand
import com.gu.zuora.creditor.Types.SerialisedJson
import org.scalatest.FlatSpec

class ZuoraExportGeneratorTest extends FlatSpec {

  val validCommand = new ExportCommand {
    override def getJSON: SerialisedJson = """{"valid":"command"}"""
  }

  val invalidCommand = new ExportCommand {
    override def getJSON: SerialisedJson = """{"invalid":"command"}"""
  }

  implicit val zuoraRestClient = new TestRestClient {
    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = {
      assert(path == "object/export")
      if (commandJSON == validCommand.getJSON) {
        """{"Id":"baz"}"""
      } else {
        ""
      }
    }
  }

  behavior of "ZuoraExportGeneratorTest"

  it should "extractExportId" in {
    val generator = new ZuoraExportGenerator(validCommand)
    assert(generator.extractExportId("""{"foo":"bar"}""").isEmpty)
    assert(generator.extractExportId("""{"Id":""}""").isEmpty)
    assert(generator.extractExportId("""{"Id": 1}""").isEmpty)
    assert(generator.extractExportId("""{Id: "bar"}""").isEmpty)
    assert(generator.extractExportId("""{"Id":"bar"}""").contains("bar"))
  }

  it should "generate" in {
    val generator1 = new ZuoraExportGenerator(validCommand)
    assert(generator1.generate().contains("baz"))

    val generator2 = new ZuoraExportGenerator(invalidCommand)
    assert(generator2.generate().isEmpty)
  }


}
