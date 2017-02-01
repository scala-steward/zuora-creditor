package com.gu.zuora.crediter

import com.gu.zuora.crediter.Types.{RawCSVText, SerialisedJson, ZuoraSoapClient}
import com.gu.zuora.soap.{Amend, AmendResponse, CallOptions, Create, CreateResponse, Delete, DeleteResponse, Error, Execute, ExecuteResponse, Generate, GenerateResponse, GetUserInfo, GetUserInfoResponse, InvalidQueryLocatorFault, InvalidTypeFault, LoginFault, LoginResponse, MalformedQueryFault, Query, QueryMore, QueryOptions, QueryResult, RasdRequest, RasdResponse, SaveResult, SessionHeader, Subscribe, SubscribeResponse, UnexpectedErrorFault, Update, UpdateResponse}

import scalaxb.Soap11Fault

class TestSoapClient extends ZuoraSoapClient {
  override def login(username: Option[String], password: Option[String]): Either[Soap11Fault[LoginFault], LoginResponse] = ???
  override def subscribe(subscribe: Subscribe, sessionHeader: SessionHeader): Either[Soap11Fault[UnexpectedErrorFault], SubscribeResponse] = ???
  override def create(create: Create, callOptions: CallOptions, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], CreateResponse] = ???
  override def generate(generate: Generate, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], GenerateResponse] = ???
  override def update(update: Update, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], UpdateResponse] = ???
  override def query(query: Query, queryOptions: QueryOptions, sessionHeader: SessionHeader): Either[Soap11Fault[MalformedQueryFault], QueryResult] = ???
  override def queryMore(queryMore: QueryMore, queryOptions: QueryOptions, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidQueryLocatorFault], QueryResult] = ???
  override def delete(delete: Delete, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], DeleteResponse] = ???
  override def getUserInfo(getUserInfo: GetUserInfo, sessionHeader: SessionHeader): Either[Soap11Fault[UnexpectedErrorFault], GetUserInfoResponse] = ???
  override def rasd(rasdRequest: RasdRequest, sessionHeader: SessionHeader): Either[Soap11Fault[UnexpectedErrorFault], RasdResponse] = ???
  override def amend(amend: Amend, sessionHeader: SessionHeader): Either[Soap11Fault[UnexpectedErrorFault], AmendResponse] = ???
  override def execute(execute: Execute, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], ExecuteResponse] = ???
}
object TestSoapClient {

  def getSuccessfulCreateResponse(source: Seq[Any]) = new TestSoapClient {
    override def create(create: Create, callOptions: CallOptions, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], CreateResponse] = {
      val zObjects = create.zObjects
      assert(zObjects.size == source.size)
      Right[Soap11Fault[InvalidTypeFault], CreateResponse](CreateResponse(
        result = zObjects.map(obj => SaveResult(Id = obj.Id))
      ))
    }
  }

  def getUnsuccessfulCreateResponseForHeadSource(source: Seq[Any]) = new TestSoapClient {
    override def create(create: Create, callOptions: CallOptions, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], CreateResponse] = {
      val zObjects = create.zObjects
      assert(zObjects.size == source.size)
      Right[Soap11Fault[InvalidTypeFault], CreateResponse](CreateResponse(
        result = zObjects.headOption.map(adjustment => SaveResult(
          Errors = Seq(Some(Error(Message = Some(Some("getUnsuccessfulCreateResponseForHeadSource error")))))
        )).toSeq ++ zObjects.tail.map(adjustment => SaveResult(Id = adjustment.Id))
      ))
    }
  }
}

class TestRestClient extends ZuoraRestClient {
  override def makeRestGET(path: String): SerialisedJson = ???
  override def downloadFile(path: String): RawCSVText = ???
  override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = ???
}

class TestZuoraClients extends ZuoraClients{
  override def zuoraSoapClient: ZuoraSoapClient = new TestSoapClient
  override def zuoraRestClient: ZuoraRestClient = new TestRestClient
  override def getSoapAPISession: Option[SessionHeader] = Some(SessionHeader("randomsessionid"))
}
