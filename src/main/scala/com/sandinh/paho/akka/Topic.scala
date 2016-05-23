package com.sandinh.paho.akka

import akka.actor.{Actor, ActorRef, Terminated}
import scala.collection.mutable

/** each topic is managed by a Topic actor - which is a child actor of MqttPubSub FSM - with the same name as the topic */
private class Topic extends Actor {
  private[this] val subscribers = mutable.Set.empty[ActorRef]

  def receive = {
    case msg: Message =>
      subscribers foreach (_ ! msg)

    case Subscribe(_, ref, _) =>
      context watch ref
      subscribers += ref

    // note: The watching actor will receive a Terminated message even if the watched actor has already been terminated at the time of registration
    // see http://doc.akka.io/docs/akka/2.4.6/scala/actors.html#Lifecycle_Monitoring_aka_DeathWatch
    case Terminated(ref) =>
      subscribers -= ref
      context.parent ! SubscriberTerminated(ref)
      if (subscribers.isEmpty) context stop self
  }
}
