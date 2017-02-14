package com.gu.zuora.creditor

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.zuora.creditor.Types.KeyValue
import com.gu.zuora.creditor.holidaysuspension.{CreateHolidaySuspensionCredit, GetNegativeHolidaySuspensionInvoices}

import scala.collection.JavaConverters._

class Lambda extends RequestHandler[KeyValue, KeyValue] with Logging {

  private implicit val zuoraClients = ZuoraAPIClientsFromEnvironment
  private implicit val zuoraRestClient = zuoraClients.zuoraRestClient

  val exportCommands = Map(
    "GetNegativeHolidaySuspensionInvoices" -> GetNegativeHolidaySuspensionInvoices
  )

  override def handleRequest(event: KeyValue, context: Context): KeyValue = {
    val shouldScheduleReport = event.containsKey("scheduleReport")
    val shouldCreditInvoices = event.containsKey("creditInvoicesFromExport")

    if (shouldScheduleReport) {
      val maybeExportId = for {
        exportCommand <- exportCommands.get(event.get("scheduleReport"))
        exportId <- new ZuoraExportGenerator(exportCommand).generate()
      } yield {
        Map("creditInvoicesFromExport" -> exportId)
      }
      (maybeExportId getOrElse Map("nothingMoreToDo" -> true.toString)).asJava
    } else if (shouldCreditInvoices) {
      val invoiceCreditor = new CreditTransferService(CreateHolidaySuspensionCredit)
      val exportId = event.get("creditInvoicesFromExport")
      Map("numberOfInvoicesCredited" -> invoiceCreditor.processExportFile(exportId).toString).asJava
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString).asJava
    }
  }

}



