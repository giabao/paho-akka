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
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._
import Docker.Process
import scala.util.Random

object BenchBase {
  val count = 10000
}
class BenchBase(_system: ActorSystem, benchName: String, brokerUrl: String, waitSeconds: Int)
    extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {
  import system.dispatcher, BenchBase._

  override def afterAll() = TestKit.shutdownActorSystem(system)

  private def test = {
      val qos = 0
      val topic = "paho-akka/BenchSpec" + Random.nextLong()

      val subs = system.actorOf(Props(classOf[SubsActor], testActor, topic, qos, brokerUrl))
      val ack = expectMsgType[SubscribeAck](10.seconds)
      ack.fail shouldBe None

      system.actorOf(Props(classOf[PubActor], count, topic, qos, brokerUrl))

      println(s"$benchName start publish $count msg")
      val timeStart = System.currentTimeMillis()

      implicit val askTimeout: Timeout = Timeout(20, MILLISECONDS)
      def askAfter = akka.pattern
        .after(1.second, system.scheduler)(subs ? SubsActorReport)
        .mapTo[Int]

      @tailrec
      def askLoop(runCount: Int): Future[Done] = {
        if (runCount >= waitSeconds) {
          Future.failed(new Exception(s"runCount exceed $waitSeconds"))
        } else {
          val receivedCount = askAfter.futureValue.ensuring(_ <= count)
          println(s"$benchName/$runCount: received $receivedCount = ${receivedCount * 100.0 / count}%")
          if (receivedCount == count) Future.successful(Done)
          else askLoop(runCount + 1)
        }
      }
      askLoop(1).futureValue shouldBe Done
      println(s"$benchName done in ${(System.currentTimeMillis() - timeStart).toDouble / 1000} seconds")
    }

  "MqttPubSub" must s"bench $brokerUrl" in {
    System.getenv("PAHO_CLIENT_VERSION") match {
      case v if v == "1.2.0" || v != null && v.startsWith("1.2.1-SNAPSHOT") => pendingUntilFixed(test)
      case _ => test
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(60, Seconds), Span(1, Second))
}

class LocalBenchSpec extends BenchBase(ActorSystem("L"), "L", "tcp://localhost:2883", 5) with BrokerHelper {
  protected val logger = org.log4s.getLogger
  private[this] var broker: Process = _
  override def beforeAll() = {
    broker = startBroker(port = 2883)
  }
  override def afterAll() = {
    super.afterAll()
    if (broker != null) broker.destroy()
  }
}
class RemoteBenchSpec extends BenchBase(ActorSystem("R"), "R", "tcp://test.mqtt.ohze.net:1883", 40)

private class PubActor(count: Int, topic: String, qos: Int, brokerUrl: String) extends Actor {
  private val pubsub = {
    val conOpt = ConnOptions(maxInflight = BenchBase.count).get
    val cfg = PSConfig(brokerUrl, conOpt = conOpt, stashCapacity = BenchBase.count)
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
