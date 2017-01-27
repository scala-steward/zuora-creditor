package com.gu.zuora.crediter.holidaysuspension

object Main {
  def main(args: Array[String]): Unit = {
    val map1 = Seq("scheduleReport" -> "true").toMap
    val map2 = Seq("creditInvoicesFromExport" -> "2c92c0f859b047b60159cafb920702bd").toMap
    val lambda = new Lambda
    lambda.handleRequest(map2, null)
    System.exit(0)
  }
}
