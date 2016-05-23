package com.sandinh.paho.akka

import java.net.{URLDecoder, URLEncoder}
import akka.actor.{Actor, ActorRef, FSM, Props, Terminated}
import org.eclipse.paho.client.mqttv3._
import MqttException.REASON_CODE_CLIENT_NOT_CONNECTED
import MqttConnectOptions.CLEAN_SESSION_DEFAULT
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

object MqttPubSub {
  private val logger = org.log4s.getLogger
  //++++ public message classes ++++//
  class Publish(val topic: String, payload: Array[Byte], qos: Int = 0) {
    def message() = {
      val msg = new MqttMessage(payload)
      msg.setQos(qos)
      msg
    }
  }

  /** TODO support wildcards subscription
    * TODO support Unsubscribe
    */
  case class Subscribe(topic: String, ref: ActorRef, qos: Int = 0)

  case class SubscribeAck(subscribe: Subscribe, fail: Option[Throwable])

  class Message(val topic: String, val payload: Array[Byte])

  //++++ FSM stuff ++++//
  sealed trait S
  case object SDisconnected extends S
  case object SConnected extends S

  //++++ internal FSM event messages ++++//
  private case object Connect
  private case object Connected
  private case object Disconnected
  /** @param ref the subscriber actor: Subscribe.ref */
  private case class SubscriberTerminated(ref: ActorRef)
  /** subscribe ack trigger by calling underlying mqtt client.subscribe */
  private case class UnderlyingSubsAck(topic: String, fail: Option[Throwable])

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

  private class PubSubMqttCallback(owner: ActorRef) extends MqttCallback {
    def connectionLost(cause: Throwable): Unit = {
      logger.error(cause)("connection lost")
      owner ! Disconnected
    }
    /** only logging */
    def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      logger.debug("delivery complete " + java.util.Arrays.toString(token.getTopics.asInstanceOf[Array[AnyRef]]))
    }
    def messageArrived(topic: String, message: MqttMessage): Unit = {
      logger.debug(s"message arrived $topic")
      owner ! new Message(topic, message.getPayload)
    }
  }

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

  //ultilities
  @inline private def urlEnc(s: String) = URLEncoder.encode(s, "utf-8")
  @inline private def urlDec(s: String) = URLDecoder.decode(s, "utf-8")

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
    */
  case class PSConfig(
      brokerUrl:         String,
      userName:          String         = null,
      password:          String         = null,
      stashTimeToLive:   Duration       = 1.minute,
      stashCapacity:     Int            = 8000,
      reconnectDelayMin: FiniteDuration = 10.millis,
      reconnectDelayMax: FiniteDuration = 30.seconds,
      cleanSession:      Boolean        = CLEAN_SESSION_DEFAULT
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
      opt.setCleanSession(cleanSession)
      opt
    }
  }
}

import MqttPubSub._

/** Notes:
  * 1. MqttClientPersistence will be set to null. @see org.eclipse.paho.client.mqttv3.MqttMessage#setQos(int)
  * 2. MQTT client will auto-reconnect
  */
class MqttPubSub(cfg: PSConfig) extends FSM[S, Unit] {
  //setup MqttAsyncClient without MqttClientPersistence
  private[this] val client = {
    val c = new MqttAsyncClient(cfg.brokerUrl, MqttAsyncClient.generateClientId(), null)
    c.setCallback(new PubSubMqttCallback(self))
    c
  }
  //setup Connnection IMqttActionListener
  private[this] val conListener = new ConnListener(self)

  private[this] val subsListener = new SubscribeListener(self)

  /** Use to stash the Publish messages when disconnected.
    * `Long` in Elem type is System.nanoTime when receive the Publish message.
    * We need republish to the underlying mqtt client when go to connected.
    */
  private[this] val pubStash = mutable.Queue.empty[(Long, Publish)]

  /** Use to stash the Subscribe messages when disconnected.
    * We need resubscribe all subscriptions to the underlying mqtt client when go to connected
    * , so we store in a separated stash (instead of merging pubStash & subStash).
    */
  private[this] val subStash = mutable.Queue.empty[Subscribe]

  /** contains all successful `Subscribe`.
    * + add when the underlying client notify that the corresponding topic is successful subscribed.
    * + remove when the Subscribe.ref actor is terminated.
    * + use to re-subscribe after reconnected.
    */
  private[this] val subscribed = mutable.Set.empty[Subscribe]

  /** contains all subscribing `Subscribe`.
    * + always add when calling the underlying client.subscribe.
    * + always remove when the call is failed and/or remove later when the underlying client notify that the
    * subscription has either Success or Failed.
    */
  private[this] val subscribing = mutable.Set.empty[Subscribe]

  //reconnect attempt count, reset when connect success
  private[this] var connectCount = 0

  //++++ FSM logic ++++
  startWith(SDisconnected, Unit)

  when(SDisconnected) {
    case Event(Connect, _) =>
      logger.info(s"connecting to ${cfg.brokerUrl}..")
      //only receive Connect when client.isConnected == false so its safe here to call client.connect
      try {
        client.connect(cfg.conOpt, null, conListener)
      } catch {
        case NonFatal(e) =>
          logger.error(e)(s"can't connect to $cfg")
          delayConnect()
      }
      connectCount += 1
      stay()

    case Event(Connected, _) =>
      connectCount = 0
      subscribed foreach doSubscribe
      while (subStash.nonEmpty) self ! subStash.dequeue()
      //remove expired Publish messages
      if (cfg.stashTimeToLive.isFinite())
        pubStash.dequeueAll(_._1 + cfg.stashTimeToLive.toNanos < System.nanoTime)
      while (pubStash.nonEmpty) self ! pubStash.dequeue()._2
      goto(SConnected)

    case Event(x: Publish, _) =>
      if (pubStash.length > cfg.stashCapacity)
        while (pubStash.length > cfg.stashCapacity / 2)
          pubStash.dequeue()
      pubStash += (System.nanoTime -> x)
      stay()

    case Event(x: Subscribe, _) =>
      subStash += x
      stay()
  }

  when(SConnected) {
    case Event(p: Publish, _) =>
      try {
        client.publish(p.topic, p.message())
      } catch {
        //underlying client can be disconnected when this FSM is in state SConnected. See ResubscribeSpec
        case e: MqttException if e.getReasonCode == REASON_CODE_CLIENT_NOT_CONNECTED =>
          self ! Disconnected //delayConnect & goto SDisconnected
          self ! p //stash p
        case NonFatal(e) =>
          logger.error(e)(s"can't publish to ${p.topic}")
      }
      stay()

    case Event(sub: Subscribe, _) =>
      context.child(urlEnc(sub.topic)) match {
        case Some(t) => t ! sub //Topic t will (only) store & watch sub.ref
        case None    => doSubscribe(sub)
      }
      stay()

    //don't need handle Terminated(topicRef) in state SDisconnected
    case Event(Terminated(topicRef), _) =>
      try {
        client.unsubscribe(urlDec(topicRef.path.name))
      } catch {
        case e: MqttException if e.getReasonCode == REASON_CODE_CLIENT_NOT_CONNECTED => //do nothing
        case NonFatal(e) => logger.error(e)(s"can't unsubscribe from ${topicRef.path.name}")
      }
      stay()
  }

  whenUnhandled {
    case Event(msg: Message, _) =>
      context.child(urlEnc(msg.topic)) foreach (_ ! msg)
      stay()

    case Event(UnderlyingSubsAck(topic, fail), _) =>
      val topicSubs = subscribing.filter(_.topic == topic)
      fail match {
        case None =>
          val encTopic = urlEnc(topic)
          val topicActor = context.child(encTopic) match {
            case None =>
              val t = context.actorOf(Props[Topic], name = encTopic)
              //TODO consider if we should use `handleChildTerminated` by override `SupervisorStrategy` instead of watch?
              context watch t
            case Some(t) => t
          }
          topicSubs.foreach { sub =>
            subscribed += sub
            topicActor ! sub
          }
        case Some(e) => //do nothing
      }
      topicSubs.foreach { sub =>
        sub.ref ! SubscribeAck(sub, fail)
        subscribing -= sub
      }
      stay()

    case Event(SubscriberTerminated(ref), _) =>
      subscribed.retain(_.ref != ref)
      stay()

    case Event(Disconnected, _) =>
      delayConnect()
      goto(SDisconnected)
  }

  private def doSubscribe(sub: Subscribe): Unit = {
    //Given:
    //  val subs = subscribing.filter(_.topic == sub.topic)
    //We need call `client.subscribe` when:
    //  subs.isEmpty => subscribe first time
    //  subs.forall(_.qos < sub.qos) => resubscribe with higher qos
    if (subscribing.forall(x => x.topic != sub.topic || x.qos < sub.qos))
      try {
        client.subscribe(sub.topic, sub.qos, null, subsListener)
      } catch {
        case e: MqttException if e.getReasonCode == REASON_CODE_CLIENT_NOT_CONNECTED =>
          self ! Disconnected //delayConnect & goto SDisconnected
          self ! sub //stash Subscribe
        case NonFatal(e) =>
          self ! UnderlyingSubsAck(sub.topic, Some(e))
          logger.error(e)(s"subscribe call fail ${sub.topic}")
      }
    subscribing += sub
  }

  private def delayConnect(): Unit = {
    val delay = cfg.connectDelay(connectCount)
    logger.info(s"delay $delay before reconnect")
    setTimer("reconnect", Connect, delay)
  }

  initialize()

  self ! Connect
}
