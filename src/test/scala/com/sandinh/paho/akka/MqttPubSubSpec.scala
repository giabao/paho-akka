package com.sandinh.paho.akka

import java.net.URLEncoder

import akka.actor.Actor.Receive
import akka.actor.{PoisonPill, Actor, ActorSystem}
import akka.testkit._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Second, Span}
import scala.concurrent.duration._
import com.sandinh.paho.akka.MqttPubSub.{SubscribeAck, Subscribe, SConnected, SDisconnected}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Future
import MqttPubSub._

class MqttPubSubSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers
    with BeforeAndAfterAll with ScalaFutures {
  import system.dispatcher

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() = TestKit.shutdownActorSystem(system)

  lazy val pubsub = TestFSMRef(new MqttPubSub("tcp://test.mosquitto.org:1883", null, null))

  "MqttPubSub" must {
    "start, subscribe, publish & receive messages" in {
      pubsub.stateName shouldBe SDisconnected

      akka.pattern.after(5.seconds, system.scheduler)(Future(pubsub.stateName)).futureValue shouldBe SConnected

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

private class SubscribeActor extends Actor {
  override def receive: Receive = ???
}