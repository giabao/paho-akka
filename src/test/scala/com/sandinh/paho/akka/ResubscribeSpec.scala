package com.sandinh.paho.akka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import com.sandinh.paho.akka.MqttPubSub._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import sys.process._

//https://github.com/giabao/paho-akka/issues/2
class ResubscribeSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers
    with BeforeAndAfterAll with ScalaFutures {

  def this() = this(ActorSystem("ResubscribeSpec"))
  override def afterAll() = TestKit.shutdownActorSystem(system)

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )

  "MqttPubSub" must {
    "resubsribe after brocker restart" in {
      val topic = "com.sandinh.paho.akka/ResubscribeSpec"

      //MqttPubSub can operate even when broker not started
      val pubsub = TestFSMRef(new MqttPubSub(PSConfig("tcp://localhost:1883")))
      val subscribe = Subscribe(topic, self, 2)
      pubsub ! subscribe

      //MqttPubSub can receive Publish before connected to broker
      val payload = "payload".getBytes
      pubsub ! new Publish(topic, payload, 2)

      //start broker
      val p = "/usr/sbin/mosquitto".run(processLogger("mosquitto"))

      expectMsg(SubscribeAck(subscribe, None))

      val msg = expectMsgType[Message]
      msg.topic shouldBe topic
      msg.payload shouldEqual payload

      //stop broker
      p.destroy()

      val payload2 = "payload2".getBytes
      pubsub ! new Publish(topic, payload2, 2)

      //then start broker again
      val p2 = "/usr/sbin/mosquitto".run(processLogger("mosquitto2"))

      val msg2 = expectMsgType[Message]
      msg2.topic shouldBe topic
      msg2.payload shouldEqual payload2

      p2.destroy()
    }
  }
}
