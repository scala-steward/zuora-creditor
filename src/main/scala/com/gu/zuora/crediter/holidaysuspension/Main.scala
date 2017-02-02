package com.gu.zuora.crediter.holidaysuspension

import scala.collection.JavaConverters._

object Main {
  def main(args: Array[String]): Unit = {
    val map1 = Map("scheduleReport" -> "true")
    val map2 = Map("creditInvoicesFromExport" -> "2c92c0f859b047b60159cafb920702bd")
    val lambda = new Lambda
    lambda.handleRequest(map1.asJava, null)
    System.exit(0)
  }
}
