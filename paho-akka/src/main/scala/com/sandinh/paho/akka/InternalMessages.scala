package com.sandinh.paho.akka

import akka.actor.ActorRef

//++++ internal FSM event messages ++++//
private case object Connect
private case object Connected
private case object Disconnected

/** @param ref the subscriber actor: Subscribe.ref */
private case class SubscriberTerminated(ref: ActorRef)

/** subscribe ack trigger by calling underlying mqtt client.subscribe */
private case class UnderlyingSubsAck(topic: String, fail: Option[Throwable])
