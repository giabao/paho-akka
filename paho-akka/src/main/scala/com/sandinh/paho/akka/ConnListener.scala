package com.sandinh.paho.akka

import akka.actor.ActorRef
import org.eclipse.paho.client.mqttv3.{IMqttActionListener, IMqttToken}
import MqttPubSub.logger

private class ConnListener(owner: ActorRef) extends IMqttActionListener {
  def onSuccess(asyncActionToken: IMqttToken): Unit = {
    logger.info("connected")
    owner ! Connected
  }

  def onFailure(asyncActionToken: IMqttToken, e: Throwable): Unit = {
    logger.error("connect failed", e)
    owner ! Disconnected
  }
}

private class PublishListener(owner: ActorRef, qos0: Boolean)
    extends IMqttActionListener {
  override def onSuccess(t: IMqttToken): Unit =
    owner ! PublishComplete(qos0)
  override def onFailure(t: IMqttToken, e: Throwable): Unit = {
    logger.error("publish failed", e)
    owner ! PublishComplete(qos0)
  }
}
