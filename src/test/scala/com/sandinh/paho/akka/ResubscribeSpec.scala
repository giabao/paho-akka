package com.sandinh.paho.akka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import sys.process._

//https://github.com/giabao/paho-akka/issues/2
class ResubscribeSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers
    with BeforeAndAfterAll with ScalaFutures {

  def this() = this(ActorSystem("ResubscribeSpec"))
  override def afterAll() = {
    if (brocker != null) brocker.destroy()
    if (brocker2 != null) brocker2.destroy()
    TestKit.shutdownActorSystem(system)
  }

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )

  private[this] var brocker: Process = null
  private[this] var brocker2: Process = null

  private def expectMqttMsg(topic: String, payload: Array[Byte]): Unit = {
    val msg = expectMsgType[Message]
    msg.topic shouldBe topic
    msg.payload shouldEqual payload
  }
  val topic = "com.sandinh.paho.akka/ResubscribeSpec"
  val payload = "payload".getBytes

  "MqttPubSub" must {
    val pubsub = TestFSMRef(new MqttPubSub(PSConfig("tcp://localhost:1883")), "pubsub")
    val subscribe = Subscribe(topic, self, 2)

    "Can Subscribe before starting broker" in {
      pubsub ! subscribe

      brocker = "/usr/sbin/mosquitto -v".run(processLogger("mosquitto"))

      expectMsg(SubscribeAck(subscribe, None))

      pubsub ! new Publish(topic, payload, 2)
      expectMqttMsg(topic, payload)
    }

    "Can resubscribe after broker restart" in {
      brocker.destroy()
      brocker.exitValue()
      brocker = null

      brocker2 = "/usr/sbin/mosquitto -v".run(processLogger("mosquitto2"))

      expectMsg(SubscribeAck(subscribe, None))

      val payload2 = "payload2".getBytes
      pubsub ! new Publish(topic, payload2, 2)

      expectMqttMsg(topic, payload2)
    }
  }
}
