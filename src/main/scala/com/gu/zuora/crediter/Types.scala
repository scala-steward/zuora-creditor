package com.gu.zuora.crediter

import com.gu.zuora.crediter.Models.{ExportFile, NegativeInvoiceFileLine}
import com.gu.zuora.soap.Soap

import scalaj.http.HttpRequest

object Types {
  type ErrorMessage = String
  type ExportId = String
  type FileId = String
  type KeyValue = Map[String, String]
  type NegativeInvoiceCSVFile = ExportFile[NegativeInvoiceFileLine]
  type SerialisedJson = String
  type ZOQLQueryFragment = String
  type CreditBalanceAdjustmentIDs = Seq[String]
  type ZuoraRestClient = String => HttpRequest
  type ZuoraSoapClient = Soap
}

trait WithZuoraClients {

}