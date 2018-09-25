package com.example

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class BaseApp(system: ActorSystem) {

  val sagaTimeout: FiniteDuration = 1.hour

  val bankAccountRegion: ActorRef = ClusterSharding(system).start(
    typeName = "bank-account",
    entityProps = Props[BankAccount],
    settings = ClusterShardingSettings(system),
    extractEntityId = BankAccount.extractEntityId,
    extractShardId = BankAccount.extractShardId
  )

  val bankAccountSagaRegion: ActorRef = ClusterSharding(system).start(
    typeName = "bank-account-saga",
    entityProps = BankAccountSaga.props(bankAccountRegion, sagaTimeout),
    settings = ClusterShardingSettings(system),
    extractEntityId = BankAccountSaga.extractEntityId,
    extractShardId = BankAccountSaga.extractShardId
  )

  val httpServerHost: String = "localhost"
  val httpServerPort: Int = 9090
  val httpRequestTimeout: FiniteDuration = 5.seconds
  val httpServer: BankAccountHttpServer = createHttpServer()

  /**
    * Main function for running the app.
    */
  protected def run(): Unit = {
    Await.ready(system.whenTerminated, Duration.Inf)
  }

  /**
    * Create Akka Http Server
    *
    * @return BankAccountHttpServer
    */
  private def createHttpServer(): BankAccountHttpServer =
    new BankAccountHttpServer(
      httpServerHost,
      httpServerPort,
      bankAccountRegion,
      bankAccountSagaRegion
    )(system)
}
