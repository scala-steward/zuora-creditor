package com.gu.zuora.crediter

import java.lang.System.getenv
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.util.Base64
import com.gu.zuora.crediter.Types.{RawCSVText, ZuoraSoapClientError, SerialisedJson}
import com.gu.zuora.soap.{CallOptions, Create, CreateResponse, SessionHeader, Soap, ZObjectable}

import scala.reflect.internal.util.StringOps
import scalaj.http.HttpOptions.readTimeout
import scalaj.http.{HttpRequest, _}

trait ZuoraRestClient {
  def makeRestGET(path: String): SerialisedJson
  def downloadFile(path: String): RawCSVText
  def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson
}

trait ZuoraSoapClient {
  // https://knowledgecenter.zuora.com/DC_Developers/SOAP_API/E_SOAP_API_Calls/create_call
  val maxNumberOfCreateObjects = 50
  def create(zObjects: Seq[ZObjectable]): Either[ZuoraSoapClientError, CreateResponse]
}

trait ZuoraAPIClients {
  def zuoraRestClient: ZuoraRestClient
  def zuoraSoapClient: ZuoraSoapClient
}

object ZuoraAPIClientsFromEnvironment extends ZuoraAPIClients with Logging {

  private def decryptSecret(cyphertext: String) = {
    if (getenv("SkipSecretDecryption") == "true") {
      cyphertext
    } else {
      val encryptedKey = ByteBuffer.wrap(Base64.decode(cyphertext))
      val client = AWSKMSClientBuilder.defaultClient
      val request = new DecryptRequest().withCiphertextBlob(encryptedKey)
      val plainTextKey = client.decrypt(request).getPlaintext
      new String(plainTextKey.array(), Charset.forName("UTF-8"))
    }
  }

  private val zuoraApiAccessKeyId = getenv("ZuoraApiAccessKeyId")
  private val zuoraApiSecretAccessKey = decryptSecret(getenv("ZuoraApiSecretAccessKey"))
  private val zuoraApiHost = getenv("ZuoraApiHost")
  private val zuoraApiTimeout = StringOps.oempty(getenv("ZuoraApiTimeout")).headOption.getOrElse("10000").toInt

  private def makeRequest(pathSuffix: String): HttpRequest = Http(s"https://rest.$zuoraApiHost/v1/$pathSuffix")
    .header("Content-type", "application/json")
    .header("Charset", "UTF-8")
    .header("Accept", "application/json")
    .header("apiAccessKeyId", zuoraApiAccessKeyId)
    .header("apiSecretAccessKey", zuoraApiSecretAccessKey)
    .option(readTimeout(zuoraApiTimeout))

  lazy val zuoraSoapClient: ZuoraSoapClient = new ZuoraSoapClient {

    private val soapCallOptions = CallOptions(useSingleTransaction = Some(Some(false)))

    private val endpoint = s"https://${if (!zuoraApiHost.startsWith("apisandbox")) "api." else ""}$zuoraApiHost/apps/services/a/83.0"

    logger.info(s"Instantiating SOAP client to endpoint: $endpoint")

    private val service: Soap = new com.gu.zuora.soap.SoapBindings with scalaxb.Soap11Clients with scalaxb.DispatchHttpClients {
      override def baseAddress: URI = URI.create(endpoint)
    }.service

    logger.info(s"Instantiated SOAP client successfully")

    private val sessionHeader = {
      val loginResponse = service.login(Some(zuoraApiAccessKeyId), Some(zuoraApiSecretAccessKey))
      if (loginResponse.isLeft) {
        logger.error(s"Unable to log in to Zuora API. Reason: " + loginResponse.left.toOption.mkString)
      }
      for {
        response <- loginResponse.right.toOption
        result <- response.result
        sessionId <- result.Session
      } yield {
        SessionHeader(sessionId)
      }
    }

    override def create(zObjects: Seq[ZObjectable]): Either[ZuoraSoapClientError, CreateResponse] = {
      sessionHeader.map { session =>
        val response = service.create(Create(zObjects), soapCallOptions, session)
        if (response.isLeft) {
          Left(response.left.get.detail.flatMap(_.FaultMessage).flatten.getOrElse("Unknown SOAP error"))
        } else {
          Right(response.right.get)
        }
      } getOrElse Left("No session")
    }
  }

  lazy val zuoraRestClient = new ZuoraRestClient {
    override def makeRestGET(pathSuffix: String): SerialisedJson = makeRequest(pathSuffix).asString.body
    override def downloadFile(pathSuffix: String): RawCSVText = makeRequest(pathSuffix).asString.body
    override def makeRestPOST(pathSuffix: String)(commandJSON: SerialisedJson): SerialisedJson = makeRequest(pathSuffix).postData(commandJSON).asString.body
  }

}
