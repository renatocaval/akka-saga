package com.example.bankaccount

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{as, complete, entity, path, post}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import spray.json._
import BankAccountCommands._

import scala.concurrent.ExecutionContext

/**
  * A wrapper to start a saga containing bank account transactional commands.
  */
case class StartBankAccountTransaction(deposits: Seq[DepositFundsDto], withdrawals: Seq[WithdrawFundsDto])

/**
  * A DTO for WithdrawFunds.
  */
case class DepositFundsDto(accountNumber: AccountNumber, amount: BigDecimal)

/**
  * A DTO for WithdrawFunds.
  */
case class WithdrawFundsDto(accountNumber: AccountNumber, amount: BigDecimal)

/**
  * Json support for BankAccountHttpRoutes.
  */
trait BankAccountJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  import BankAccountsQuery._

  implicit val createBankAccountFormat = jsonFormat2(CreateBankAccount)
  implicit val depositFundsFormat = jsonFormat2(DepositFundsDto)
  implicit val withdrawFundsFormat = jsonFormat2(WithdrawFundsDto)
  implicit val bankAccountProjectionFormat = jsonFormat2(BankAccountProjection)
  implicit val bankAccountsProjectionsFormat = jsonFormat1(BankAccountProjections)

  implicit val startBankAccountTransactionFormat = jsonFormat2(StartBankAccountTransaction)
}

/**
  * Makes it easier to test this thing. Using this we can assert a known value for transaction id at test time
  * and randomly generate them at runtime.
  */
trait TransactionIdGenerator {
  def generateId: String
}

/**
  * Runtime, default impl for above trait.
  */
class TransactionIdGeneratorImpl extends TransactionIdGenerator {
  override def generateId: String = UUID.randomUUID().toString
}

/**
  * Http routes for bank account.
  */
trait BankAccountRoutes extends BankAccountJsonSupport {

  import BankAccountsQuery._
  import com.example.PersistentSagaActor._

  def bankAccountSagaRegion: ActorRef
  def bankAccountRegion: ActorRef
  def transactionIdGenerator: TransactionIdGenerator = new TransactionIdGeneratorImpl
  def bankAccountsQuery: ActorRef

  implicit val system: ActorSystem
  implicit def timeout: Timeout
  implicit def ec: ExecutionContext = system.dispatcher

  val route: Route =
    path("bank-accounts") {
      post {
        entity(as[StartBankAccountTransaction]) { dto =>
          val start = StartSaga(transactionIdGenerator.generateId, dtoToDomain((dto)))
          bankAccountSagaRegion ! start
          complete(StatusCodes.Accepted, s"Transaction accepted with id: ${start.transactionId}")
        }
      } ~
      post {
        entity(as[CreateBankAccount]) { cmd =>
          bankAccountRegion ! cmd
          complete(StatusCodes.Accepted, s"CreateBankAccount accepted with number: ${cmd.accountNumber}")
        }
      } ~
      get {
        complete(
          (bankAccountsQuery ? GetBankAccountProjections).mapTo[BankAccountProjections]
        )
      }
    }

  /**
    * Convert dto commands to list of domain commands.
    */
  private def dtoToDomain(dto: StartBankAccountTransaction): Seq[BankAccountTransactionalCommand] =
    (dto.deposits ++ dto.withdrawals).map ( c =>
      c match {
        case d: DepositFundsDto => DepositFunds(d.accountNumber, d.amount)
        case w: WithdrawFundsDto => WithdrawFunds(w.accountNumber, w.amount)
      }
  )
}
