package com.gu.zuora.crediter.holidaysuspension

import com.gu.zuora.crediter.Lambda

import scala.collection.JavaConverters._

object Main {
  def main(args: Array[String]): Unit = {
    val map1 = Map("scheduleReport" -> "GetNegativeHolidaySuspensionInvoices")
    val map2 = Map("creditInvoicesFromExport" -> "2c92c0f85a1174a9015a1809cb123630")
    val lambda = new Lambda
    lambda.handleRequest(map2.asJava, null)
    System.exit(0)
  }
}
