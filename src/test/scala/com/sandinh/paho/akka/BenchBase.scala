package com.sandinh.paho.akka

import ByteArrayConverters._

import akka.Done
import akka.actor.Actor.emptyBehavior
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Outcome, Retries}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class BenchBase(benchName: String) extends TestKit(ActorSystem("benchName"))
  with ImplicitSender with AnyFlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures with Retries {
  import system.dispatcher

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system, 1.minute)

  override def withFixture(test: NoArgTest): Outcome =
    withRetry { super.withFixture(test) }

  def benchTest(brokerUrl: String, qos: Int, maxRunCount: Int, msgCount: Int): Unit = {
      val topic = s"paho-akka/$benchName/${Random.nextLong()}"

      val subs = system.actorOf(Props(classOf[SubsActor], testActor, topic, qos, brokerUrl))
      val ack = expectMsgType[SubscribeAck](20.seconds)
      ack.fail shouldBe None

      system.actorOf(Props(classOf[PubActor], topic, qos, brokerUrl, msgCount))

      println(s"$benchName/$qos start publish $msgCount msg")
      val timeStart = System.currentTimeMillis()

      implicit val askTimeout: Timeout = Timeout(20, MILLISECONDS)
      def askAfter = akka.pattern
        .after(1.second, system.scheduler)(subs ? SubsActorReport)
        .mapTo[Int]

      @tailrec
      def askLoop(runCount: Int): Future[Done] = {
        if (runCount >= maxRunCount) {
          Future.failed(new Exception(s"runCount exceed $maxRunCount"))
        } else {
          val receivedCount = askAfter.futureValue.ensuring(_ <= msgCount)
          println(s"$benchName/$qos/$runCount: received $receivedCount = ${receivedCount * 100.0 / msgCount}%")
          if (receivedCount == msgCount) Future.successful(Done)
          else askLoop(runCount + 1)
        }
      }
      askLoop(1).futureValue shouldBe Done
      println(s"$benchName/$qos done in ${(System.currentTimeMillis() - timeStart).toDouble / 1000} seconds")
    }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(60, Seconds), Span(1, Second))
}

private class PubActor(topic: String, qos: Int, brokerUrl: String, count: Int) extends Actor {
  private val pubsub = {
    val cfg = PSConfig(
      brokerUrl,
      conOpt = ConnOptions(maxInflight = 10, maxInflightQos12 = 10),
      stashCapacity = count
    )
    context.actorOf(Props(classOf[MqttPubSub], cfg))
  }

  for (i <- 0 until count) {
    pubsub ! new Publish(topic, i.toByteArray, qos)
  }

  def receive: Receive = emptyBehavior
}

private case object SubsActorReport

private class SubsActor(reportTo: ActorRef, topic: String, qos: Int, brokerUrl: String) extends Actor {
  private val pubsub = context.actorOf(Props(classOf[MqttPubSub], PSConfig(brokerUrl)))
  pubsub ! Subscribe(topic, self, qos)

  def receive: Receive = {
    case msg @ SubscribeAck(Subscribe(`topic`, `self`, `qos`), _) =>
      reportTo ! msg
      context become ready(0)
  }

  def ready(receivedCount: Int): Receive = {
    case _: Message      => context become ready(receivedCount + 1)
    case SubsActorReport => sender() ! receivedCount
  }
}
