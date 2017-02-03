package com.gu.zuora.crediter

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.zuora.crediter.Types.KeyValue
import com.gu.zuora.crediter.holidaysuspension.{CreateHolidaySuspensionCredit, GetNegativeHolidaySuspensionInvoices}

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
      val invoiceCrediter = new CreditTransferService(CreateHolidaySuspensionCredit)
      val exportId = event.get("creditInvoicesFromExport")
      Map("numberOfInvoicesCredited" -> invoiceCrediter.processExportFile(exportId).toString).asJava
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString).asJava
    }
  }

}



