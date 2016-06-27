package com.sandinh.paho.akka

import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttConnectOptions.CLEAN_SESSION_DEFAULT
import scala.concurrent.duration._

/** @param brokerUrl ex tcp://test.mosquitto.org:1883
  * @param userName nullable
  * @param password nullable
  * @param stashTimeToLive messages received when disconnected will be stash.
  * Messages isOverdue after stashTimeToLive will be discard. See also `stashCapacity`
  * @param stashCapacity pubSubStash will be drop first haft elems when reach this size
  * @param reconnectDelayMin when received Disconnected event, we will first delay reconnectDelayMin to try Connect.
  * + if connect success => we reinit connectCount
  * + else => ConnListener.onFailure will send Disconnected to this FSM =>
  * we re-schedule Connect with {{{delay = reconnectDelayMin * 2^connectCount}}}
  * @param reconnectDelayMax max delay to retry connecting
  * @param cleanSession Sets whether the client and server should remember state across restarts and reconnects.
  * @param will A last will and testament message (and topic, and qos) that will be set on the connection.
  */
case class PSConfig(
    brokerUrl:         String,
    userName:          String         = null,
    password:          String         = null,
    stashTimeToLive:   Duration       = 1.minute,
    stashCapacity:     Int            = 8000,
    reconnectDelayMin: FiniteDuration = 10.millis,
    reconnectDelayMax: FiniteDuration = 30.seconds,
    cleanSession:      Boolean        = CLEAN_SESSION_DEFAULT,
    will:              Publish        = null
) {

  //pre-calculate the max of connectCount that: reconnectDelayMin * 2^connectCountMax ~ reconnectDelayMax
  val connectCountMax = Math.floor(Math.log(reconnectDelayMax / reconnectDelayMin) / Math.log(2)).toInt

  def connectDelay(connectCount: Int) =
    if (connectCount >= connectCountMax) reconnectDelayMax
    else reconnectDelayMin * (1L << connectCount)

  /** MqttConnectOptions */
  lazy val conOpt = {
    val opt = new MqttConnectOptions
    if (userName != null) opt.setUserName(userName)
    if (password != null) opt.setPassword(password.toCharArray)
    if (will != null) opt.setWill(will.topic, will.message().getPayload, will.message().getQos, false)
    opt.setCleanSession(cleanSession)
    opt
  }
}
