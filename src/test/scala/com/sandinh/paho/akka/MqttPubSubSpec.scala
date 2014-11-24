package com.sandinh.paho.akka

import java.net.URLEncoder
import akka.actor.{PoisonPill, ActorSystem}
import akka.testkit._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Second, Span}
import scala.concurrent.duration._
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.{Promise, Future}
import MqttPubSub._

class MqttPubSubSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers
    with BeforeAndAfterAll with ScalaFutures {
  import system.dispatcher

  def this() = this(ActorSystem("MqttPubSubSpec"))

  override def afterAll() = TestKit.shutdownActorSystem(system)

  lazy val pubsub = TestFSMRef(new MqttPubSub(PSConfig("tcp://test.mosquitto.org:1883")))

  def poll(f: => Boolean): Future[Boolean] = {
    val p = Promise[Boolean]()
    val task = system.scheduler.schedule(1.second, 1.second, new Runnable {
      def run() = if (f) p success true
    })
    p.future.andThen { case _ => task.cancel() }
  }

  "MqttPubSub" must {
    "start, subscribe, publish & receive messages" in {
      pubsub.stateName shouldBe SDisconnected

      def checkState = pubsub.stateName == SConnected
      poll(checkState).futureValue shouldBe true

      val topic = "com.sandinh.paho.akka/MqttPubSubSpec"
      val subscribe = Subscribe(topic, self, 2)
      pubsub ! subscribe
      expectMsg(SubscribeAck(subscribe))

      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(topic, "utf-8"))

      val payload = "12345".getBytes("utf-8")
      pubsub ! new Publish(topic, payload, 2)

      val msg = expectMsgType[Message]
      msg.topic shouldBe topic
      msg.payload shouldEqual payload
    }

    "unsubscirbe" in {
      val probe = TestProbe()
      val topic = "com.sandinh.paho.akka/MqttPubSubSpec/2"
      val subscribe = Subscribe(topic, probe.ref, 2)
      pubsub ! subscribe
      probe.expectMsg(SubscribeAck(subscribe))
      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(topic, "utf-8"))

      probe.ref ! PoisonPill
      akka.pattern.after(1.seconds, system.scheduler)(Future(pubsub.children))
        .futureValue.map(_.path.name) should not contain URLEncoder.encode(topic, "utf-8")
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))
}
