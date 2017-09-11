package com.sandinh.paho.akka

import java.net.URLEncoder

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Seconds, Span}

import scala.concurrent.duration._
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Future, Promise}

import scala.util.Random

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
    "start, subscribe, wildcard subscribe, publish & receive messages" in {
      pubsub.stateName shouldBe DisconnectedState

      def checkState = pubsub.stateName == ConnectedState
      poll(checkState).futureValue shouldBe true

      val topic = "paho-akka/MqttPubSubSpec" + Random.nextLong()
      val hashtagTopic = "paho-akka/MqttPubSubSpec/Wildcard/#"
      val hashtagTestTopic = "paho-akka/MqttPubSubSpec/Wildcard/" + Random.nextLong()
      val plusTopic = "paho-akka/MqttPubSubSpec/Plus/+/Subtopic"
      val plusTestTopic = "paho-akka/MqttPubSubSpec/Plus/" + Random.nextLong() + "/Subtopic"

      val subscribe = Subscribe(topic, self, 2)
      val hashtagSubscribe = Subscribe(hashtagTopic, self, 2)
      val plusSubscribe = Subscribe(plusTopic, self, 2)

      pubsub ! subscribe
      expectMsg(SubscribeAck(subscribe, None))
      pubsub ! hashtagSubscribe
      expectMsg(SubscribeAck(hashtagSubscribe, None))
      pubsub ! plusSubscribe
      expectMsg(SubscribeAck(plusSubscribe, None))

      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(topic, "utf-8"))
      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(hashtagTopic, "utf-8"))
      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(plusTopic, "utf-8"))

      val payload = "12345".getBytes("utf-8")
      pubsub ! new Publish(topic, payload, 2)

      val msg = expectMsgType[Message]
      msg.topic shouldBe topic
      msg.payload shouldEqual payload

      val hashtagPayload = "67890".getBytes("utf-8")
      pubsub ! new Publish(hashtagTestTopic, hashtagPayload, 2)

      val hashtagMsg = expectMsgType[Message]
      hashtagMsg.topic shouldBe hashtagTestTopic
      hashtagMsg.payload shouldEqual hashtagPayload

      val plusPayload = "abcde".getBytes("utf-8")
      pubsub ! new Publish(plusTestTopic, plusPayload, 2)

      val plusMsg = expectMsgType[Message]
      plusMsg.topic shouldBe plusTestTopic
      plusMsg.payload shouldEqual plusPayload
    }

    "unsubscribe" in {
      val probe = TestProbe()
      val topic = "paho-akka/MqttPubSubSpec/" + Random.nextLong()
      val subscribe = Subscribe(topic, probe.ref, 2)
      pubsub ! subscribe
      probe.expectMsg(SubscribeAck(subscribe, None))
      pubsub.children.map(_.path.name) should contain(URLEncoder.encode(topic, "utf-8"))

      probe.ref ! PoisonPill
      akka.pattern.after(1.seconds, system.scheduler)(Future(pubsub.children))
        .futureValue.map(_.path.name) should not contain URLEncoder.encode(topic, "utf-8")
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))
}
