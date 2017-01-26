package com.gu.zuora.crediter

import java.lang.System.getenv

import com.gu.zuora.crediter.Types.{ZuoraRestClient, ZuoraSoapClient}
import com.gu.zuora.soap.Soap

import scala.reflect.internal.util.StringOps
import scalaj.http.HttpOptions.readTimeout
import scalaj.http.{HttpRequest, _}

trait ZuoraClients {
  def zuoraRestClient: ZuoraRestClient
  def zuoraSoapClient: ZuoraSoapClient
}

object ZuoraClientsFromEnvironment extends ZuoraClients {

  private val zuoraApiAccessKeyId = getenv("ZuoraApiAccessKeyId")
  private val zuoraApiSecretAccessKey = getenv("ZuoraApiSecretAccessKey")
  private val zuoraApiHost = getenv("ZuoraApiHost")
  private val zuoraApiTimeout = StringOps.oempty(getenv("ZuoraApiTimeout")).headOption.getOrElse("10000").toInt

  private def getZuoraRestClient(pathSuffix: String): HttpRequest = Http(s"https://rest.$zuoraApiHost/v1/$pathSuffix")
    .header("Content-type", "application/json")
    .header("Charset", "UTF-8")
    .header("Accept", "application/json")
    .header("apiAccessKeyId", zuoraApiAccessKeyId)
    .header("apiSecretAccessKey", zuoraApiSecretAccessKey)
    .option(readTimeout(zuoraApiTimeout))

  lazy val zuoraSoapClient: Soap = new com.gu.zuora.soap.SoapBindings with scalaxb.Soap11Clients with scalaxb.DispatchHttpClients {
    override def baseAddress = new java.net.URI(s"https://$zuoraApiHost/apps/services/a/83.0")
  }.service


  def zuoraRestClient: ZuoraRestClient = getZuoraRestClient
}
