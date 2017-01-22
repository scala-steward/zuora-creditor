package com.gu.zuora.crediter

import java.lang.System.getenv

import com.gu.zuora.soap.Soap

import scala.reflect.internal.util.StringOps
import scalaj.http.HttpOptions.readTimeout
import scalaj.http.{HttpRequest, _}

object ZuoraClients {

  private val zuoraApiAccessKeyId = getenv("ZuoraApiAccessKeyId")
  private val zuoraApiSecretAccessKey = getenv("ZuoraApiSecretAccessKey")
  private val zuoraApiHost = getenv("ZuoraApiRestHost")
  private val zuoraApiTimeout = StringOps.oempty(getenv("ZuoraApiTimeout")).headOption.getOrElse("10000").toInt

  val zuoraSoapClient: Soap = new com.gu.zuora.soap.SoapBindings with scalaxb.Soap11Clients with scalaxb.DispatchHttpClients {}.service

  def zuoraRestClient(pathSuffix: String): HttpRequest = Http(s"https://$zuoraApiHost/v1/$pathSuffix")
    .header("Content-type", "application/json")
    .header("Charset", "UTF-8")
    .header("Accept", "application/json")
    .header("apiAccessKeyId", zuoraApiAccessKeyId)
    .header("apiSecretAccessKey", zuoraApiSecretAccessKey)
    .option(readTimeout(zuoraApiTimeout))
}
