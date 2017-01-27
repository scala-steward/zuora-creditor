package com.gu.zuora.crediter.holidaysuspension

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.gu.zuora.crediter.Types.KeyValue
import com.gu.zuora.crediter.{ZuoraCreditTransferService, ZuoraClientsFromEnvironment, ZuoraExportGenerator}
import com.typesafe.scalalogging.StrictLogging

class Lambda extends RequestHandler[KeyValue, KeyValue] with StrictLogging {

  implicit val zuoraClients = ZuoraClientsFromEnvironment
  val exportGenerator = new ZuoraExportGenerator(GetNegativeHolidaySuspensionInvoices)
  val invoiceCrediter = new ZuoraCreditTransferService(CreateHolidaySuspensionCredit)

  override def handleRequest(event: KeyValue, context: Context): KeyValue = {
    val shouldScheduleReport = event.get("scheduleReport").contains("true")
    val shouldCreditInvoices = event.get("creditInvoicesFromExport").exists(_.nonEmpty)

    if (shouldScheduleReport) {
      val exportId = exportGenerator.generate()
      Map("creditInvoicesFromExport" -> exportId.mkString)
    } else if (shouldCreditInvoices) {
      val invoicesTransferred = event.get("creditInvoicesFromExport").map(invoiceCrediter.processExportFile).getOrElse(0)
      Map("numberOfInvoicesCredited" -> invoicesTransferred.toString)
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString)
    }
  }

}



