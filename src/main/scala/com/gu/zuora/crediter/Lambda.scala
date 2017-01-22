package com.gu.zuora.crediter

import java.util.{Map => JMap}

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.typesafe.scalalogging.LazyLogging

class Lambda extends RequestHandler[JMap[String, Object], Unit] with LazyLogging {

  override def handleRequest(event: JMap[String, Object], context: Context): Unit = {
    val shouldScheduleReport = Option(event.get("scheduleReport")).exists(true.equals)
    val shouldCreditInvoices = Option(event.get("creditInvoices")).isDefined

    if (shouldScheduleReport) {
      if (!NegativeHolidayCreditInvoicesExportGenerator.generate()) {
        logger.error(s"Did not successfully schedule report: $event")
      }
    } else if (shouldCreditInvoices) {
      if (!CreditNoteCreator.fromExportFile(event.get("creditInvoices").toString)) {
        logger.error(s"Did not successfully download report: $event")
      }
    }
  }

}



