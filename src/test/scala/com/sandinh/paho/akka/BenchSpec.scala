package com.sandinh.paho.akka

import java.nio.ByteBuffer
import akka.actor.{ActorRef, Props, Actor, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.sandinh.paho.akka.MqttPubSub.{Message, SubscribeAck, Subscribe, Publish}
import com.sandinh.paho.akka.SubsActor.Report
import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.LoggerContext
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class BenchSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers
    with BeforeAndAfterAll with ScalaFutures {
  import system.dispatcher

  def this() = this(ActorSystem("BenchSpec"))

  override def afterAll() = TestKit.shutdownActorSystem(system)

  def muteLogging(): Unit = {
    val ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
    val cfg = ctx.getConfiguration.getLoggerConfig("com.sandinh.paho.akka")
    cfg.setLevel(Level.ERROR)
    ctx.updateLoggers()
  }

  "MqttPubSub" must {
    "bench ok" in {
      muteLogging()

      val count = 10000
      val qos = 0

      val subs = system.actorOf(Props(classOf[SubsActor], testActor, qos))
      subs ! Run
      within(10.seconds) {
        expectMsgType[SubscribeAck]
      }

      val pub = system.actorOf(Props(classOf[PubActor], count, qos))
      pub ! Run

      var receivedCount = 0
      def notDone = receivedCount < count

      implicit val askTimeout = akka.util.Timeout(1, SECONDS)
      for (delay <- 1 to 50 if notDone) {
        receivedCount = akka.pattern.after(1.seconds, system.scheduler)(subs ? Report).mapTo[Int].futureValue
        println(s"$delay: Pub $count Rec $receivedCount ~ ${receivedCount * 100.0 / count}%")
      }

      assert(!notDone)
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(60, Seconds), Span(1, Second))
}

private case object Run

private trait Common { this: Actor =>
  val pubsub = context.actorOf(MqttPubSub.props("tcp://test.mosquitto.org:1883"))
  val topic = "com.sandinh.paho.akka/BenchSpec"
}

private class PubActor(count: Int, qos: Int) extends Actor with Common {
  def receive = {
    case Run =>
      var i = 0
      while (i < count) {
        val payload = ByteBuffer.allocate(4).putInt(i).array()
        pubsub ! new Publish(topic, payload, qos)
        i += 1
      }
  }
}

private object SubsActor {
  case object Report
}

private class SubsActor(reporTo: ActorRef, qos: Int) extends Actor with Common {
  import SubsActor._
  def receive = {
    case Run => pubsub ! Subscribe(topic, self, qos)
    case msg @ SubscribeAck(Subscribe(`topic`, `self`, `qos`)) =>
      context become ready
      reporTo ! msg
  }

  private[this] var receivedCount = 0
  def ready: Receive = {
    case msg: Message => receivedCount += 1
    case Report       => sender() ! receivedCount
  }
}
