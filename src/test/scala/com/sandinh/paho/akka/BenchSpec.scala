package com.sandinh.paho.akka

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Random

object BenchBase {
  val count = 5000
}
class BenchBase(_system: ActorSystem, benchName: String, brokerUrl: String, waitSeconds: Int)
    extends TestKit(_system) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {
  import system.dispatcher, BenchBase._

  override def afterAll() = TestKit.shutdownActorSystem(system)

  private def test = {
      val qos = 0
      val topic = "paho-akka/BenchSpec" + Random.nextLong()

      val subs = system.actorOf(Props(classOf[SubsActor], testActor, topic, qos, brokerUrl))
      subs ! Run
      val ack = expectMsgType[SubscribeAck](10.seconds)
      ack.fail shouldBe None

      val pub = system.actorOf(Props(classOf[PubActor], count, topic, qos, brokerUrl))
      pub ! Run

      var receivedCount = 0

      def notDone = receivedCount < count

      println(s"$benchName start publish $count msg")
      val timeStart = System.currentTimeMillis()

      implicit val askTimeout: Timeout = Timeout(20, MILLISECONDS)
      def after[T] = akka.pattern.after[T](1.second, system.scheduler) _
      for (delay <- 1 to waitSeconds if notDone) {
        receivedCount = after(subs ? SubsActorReport).mapTo[Int].futureValue
        println(s"$benchName/$delay: received $receivedCount = ${receivedCount * 100.0 / count}%")
      }
      println(s"$benchName done in ${(System.currentTimeMillis() - timeStart).toDouble / 1000} seconds")

      assert(!notDone)
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
class RemoteBenchSpec extends BenchBase(ActorSystem("R"), "R", "tcp://test.mosquitto.org:1883", 200)

private case object Run

private class PubActor(count: Int, topic: String, qos: Int, brokerUrl: String) extends Actor {
  private val pubsub = {
    val conOpt = ConnOptions(maxInflight = BenchBase.count).get
    val cfg = PSConfig(brokerUrl, conOpt = conOpt, stashCapacity = BenchBase.count)
    context.actorOf(Props(classOf[MqttPubSub], cfg))
  }

  def receive = {
    case Run =>
      for (i <- 0 until count) {
        val payload = ByteBuffer.allocate(4).putInt(i).array()
        pubsub ! new Publish(topic, payload, qos)
      }
  }
}

private case object SubsActorReport

private class SubsActor(reportTo: ActorRef, topic: String, qos: Int, brokerUrl: String) extends Actor {
  private val pubsub = context.actorOf(Props(classOf[MqttPubSub], PSConfig(brokerUrl)))

  def receive = {
    case Run => pubsub ! Subscribe(topic, self, qos)
    case msg @ SubscribeAck(Subscribe(`topic`, `self`, `qos`), _) =>
      context become ready
      reportTo ! msg
  }

  private[this] var receivedCount = 0
  def ready: Receive = {
    case msg: Message    => receivedCount += 1
    case SubsActorReport => sender() ! receivedCount
  }
}
