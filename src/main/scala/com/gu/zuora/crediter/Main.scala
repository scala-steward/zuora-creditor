package com.gu.zuora.crediter

import scala.collection.JavaConverters._

object Main {
  def main(args: Array[String]): Unit = {
    val map1 = Seq("scheduleReport" -> Boolean.box(true)).toMap[String, Object]
    val map2 = Seq("creditInvoices" -> "2c92a0ff59b56b9e0159c6c7eae44eb4").toMap[String, Object]
    val lambda = new Lambda
    lambda.handleRequest(map2.asJava, null)
  }
}
