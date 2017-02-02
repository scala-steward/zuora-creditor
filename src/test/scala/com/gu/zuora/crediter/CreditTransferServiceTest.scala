package com.gu.zuora.crediter

import com.gu.zuora.crediter.ModelReaders._
import com.gu.zuora.crediter.Models.{CreateCreditBalanceAdjustmentCommand, ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.crediter.TestSoapClient.{getSuccessfulCreateResponse, getUnsuccessfulCreateResponseForHeadSource}
import com.gu.zuora.crediter.Types.{CreditBalanceAdjustmentIDs, ZuoraSoapClient}
import com.gu.zuora.soap.{CallOptions, Create, CreateResponse, CreditBalanceAdjustment, InvalidTypeFault, SaveResult, SessionHeader}
import org.scalatest.FlatSpec

import scalaxb.Soap11Fault

class CreditTransferServiceTest extends FlatSpec {

  val testSubscriberId = "A-S012345"

  val mockCommand = new CreateCreditBalanceAdjustmentCommand {
    override def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = ???
  }

  implicit val zuoraClients = new TestZuoraAPIClients

  val service = new CreditTransferService(mockCommand)

  behavior of "ZuoraCreditTransferServiceTest"


  it should "processExportFile" in {

  }

  it should "invoicesFromReport take a valid CSV export file" in {

    val expected = Set(
      NegativeInvoiceToTransfer("INV012345", -2.10, "A-S012345"),
      NegativeInvoiceToTransfer("INV012346", -2.11, "A-S012346")
    )
    val invoices = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.10
        |A-S012346,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    ))
    assert(invoices.size == 2)
    assert(invoices == expected)
  }

  it should "invoicesFromReport gracefully fail with an invalid CSV export file" in {

    // invalid types in the CSV etc have silent failure
    val invalidAmount = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
      |A-S012345,INV012345,2017-01-01,minustwopoundsten""".stripMargin
    ))
    assert(invalidAmount.isEmpty)

    val missingData = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance"""
    ))
    assert(missingData.isEmpty)

    val emptyResponse = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](""))
    assert(emptyResponse.isEmpty)
  }

  it should "round to the customer's benefit in processNegativeInvoicesExportLine" in {
    val roundToCustomerBenefit = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.1101""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    val negativeInvoice = roundToCustomerBenefit.head.right.get
    assert(negativeInvoice.invoiceBalance == -2.12)
    assert(negativeInvoice.transferrableBalance == 2.12)
  }

  it should "return a left in processNegativeInvoicesExportLine for bad data" in {

    val positiveAmountError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(positiveAmountError.head.isLeft)
    assert(positiveAmountError.head.left.get.startsWith("Ignored invoice INV012345"))

    val missingInvoiceNumberError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(missingInvoiceNumberError.head.isLeft)
    assert(missingInvoiceNumberError.head.left.get.startsWith("Ignored invoice  dated 2017-01-01"))

    val missingSubscriberIdError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |,INV012345,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(missingSubscriberIdError.head.isLeft)
    assert(missingSubscriberIdError.head.left.get.startsWith("Ignored invoice INV012345 dated 2017-01-01 with balance -2.10 for subscription:  as"))
  }

  it should "createCreditBalanceAdjustments given to it" in {
    val adjustmentsToCreate = Seq(
      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-A"))),
      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-B")))
    )
    implicit val zuoraClients = new TestZuoraAPIClients {
      override val zuoraSoapClient: ZuoraSoapClient = getSuccessfulCreateResponse(adjustmentsToCreate)
    }
    val service = new CreditTransferService(mockCommand)
    implicit val sessionHeader: SessionHeader = SessionHeader("foo")
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(created == Seq(
      s"Refunding-$testSubscriberId-A",
      s"Refunding-$testSubscriberId-B"
    ))
  }

  it should "not create any createCreditBalanceAdjustments in Zuora when given no adjustments to create" in {
    val adjustmentsToCreate = Seq.empty
    implicit val zuoraClients = new TestZuoraAPIClients {
      override val zuoraSoapClient: ZuoraSoapClient = getSuccessfulCreateResponse(adjustmentsToCreate)
    }
    val service = new CreditTransferService(mockCommand)
    implicit val sessionHeader: SessionHeader = SessionHeader("foo")
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(created.isEmpty)
  }

  it should "handle response failures for some createCreditBalanceAdjustments" in {
    val adjustmentsToCreate = Seq(
      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-A"))),
      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-B")))
    )
    implicit val zuoraClients = new TestZuoraAPIClients {
      override val zuoraSoapClient: ZuoraSoapClient = getUnsuccessfulCreateResponseForHeadSource(adjustmentsToCreate)
    }
    val service = new CreditTransferService(mockCommand)
    implicit val sessionHeader: SessionHeader = SessionHeader("bar")
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(created == Seq(
      s"Refunding-$testSubscriberId-B"
    ))
  }

  it should "makeCreditAdjustments" in {
    assert(service.makeCreditAdjustments(Set.empty) == Seq.empty)

    // now with data

    // Also tests the scale does not get altered
    val source = Set(
      NegativeInvoiceToTransfer("INV012345", BigDecimal(-2.1).setScale(2), testSubscriberId),
      NegativeInvoiceToTransfer("INV012346", -2.111111, testSubscriberId)
    )
    val expected: CreditBalanceAdjustmentIDs = Seq(
      s"Refunding-$testSubscriberId-INV012345-2.10",
      s"Refunding-$testSubscriberId-INV012346-2.111111"
    )

    val command2 = new CreateCreditBalanceAdjustmentCommand {
      override def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = {
        CreditBalanceAdjustment(Id = Some(Some(s"Refunding-${invoice.subscriberId}-${invoice.invoiceNumber}-${invoice.transferrableBalance}")))
      }
    }

    implicit val zuoraClients = new TestZuoraAPIClients {
      override val zuoraSoapClient = new TestSoapClient {
        override def create(create: Create, callOptions: CallOptions, sessionHeader: SessionHeader): Either[Soap11Fault[InvalidTypeFault], CreateResponse] = {
          val zObjects = create.zObjects
          assert(zObjects.size == source.size)
          Right[Soap11Fault[InvalidTypeFault], CreateResponse](CreateResponse(
            result = zObjects.map(adjustment => SaveResult(Id = adjustment.Id))
          ))
        }
      }
    }

    val service2 = new CreditTransferService(command2)

    val createdAdjustments = service2.makeCreditAdjustments(source)
    assert(createdAdjustments == expected)
  }

}
