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
    logger.error(e)("connect failed")
    owner ! Disconnected
  }
}
