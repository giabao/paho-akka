package com.sandinh.paho.akka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import Docker.Process
import org.slf4j.{Logger, LoggerFactory}

//https://github.com/giabao/paho-akka/issues/2
class ResubscribeSpec(_system: ActorSystem)
    extends TestKit(_system)
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with BrokerHelper {
  protected val logger: Logger = LoggerFactory.getLogger("ResubscribeSpec")

  def this() = this(ActorSystem("ResubscribeSpec"))
  override def afterAll() = {
    if (broker != null) broker.destroy()
    if (broker2 != null) broker2.destroy()
    TestKit.shutdownActorSystem(system)
  }

  private[this] var broker: Process = _
  private[this] var broker2: Process = _

  private def expectMqttMsg(topic: String, payload: Array[Byte]): Unit = {
    val msg = expectMsgType[Message]
    msg.topic shouldBe topic
    msg.payload shouldEqual payload
  }
  val topic = "com.sandinh.paho.akka/ResubscribeSpec"
  val payload = "payload".getBytes

  "MqttPubSub" must {
    broker = startBroker("mosquitto")
    val pubsub =
      TestFSMRef(new MqttPubSub(PSConfig("tcp://localhost:1883")), "pubsub")
    val subscribe = Subscribe(topic, self, 2)

    "Can Subscribe before starting broker" in {
      pubsub ! subscribe

      expectMsg(SubscribeAck(subscribe, None))

      pubsub ! new Publish(topic, payload, 2)
      expectMqttMsg(topic, payload)
    }

    "Can resubscribe after broker restart" in {
      logger.info("stopping mosquitto")
      broker.destroy()
      broker = null
      logger.info("stopped mosquitto")

      broker2 = startBroker("mosquitto2", wait = 3.seconds)

      expectMsg(6.seconds, SubscribeAck(subscribe, None))

      val payload2 = "payload2".getBytes
      pubsub ! new Publish(topic, payload2, 2)

      expectMqttMsg(topic, payload2)
    }
  }
}
