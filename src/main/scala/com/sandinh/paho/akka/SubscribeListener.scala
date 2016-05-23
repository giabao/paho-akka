package com.sandinh.paho.akka

import org.eclipse.paho.client.mqttv3.{IMqttActionListener, IMqttToken}
import MqttPubSub.logger
import akka.actor.ActorRef

private class SubscribeListener(owner: ActorRef) extends IMqttActionListener {
  def onSuccess(asyncActionToken: IMqttToken) = {
    val topic = asyncActionToken.getTopics()(0) //getTopics always has len == 1
    logger.info(s"subscribe success $topic")
    owner ! UnderlyingSubsAck(topic, None)
  }
  def onFailure(asyncActionToken: IMqttToken, e: Throwable) = {
    val topic = asyncActionToken.getTopics()(0) //getTopics always has len == 1
    logger.error(e)(s"subscribe fail $topic")
    owner ! UnderlyingSubsAck(topic, Some(e))
  }
}
