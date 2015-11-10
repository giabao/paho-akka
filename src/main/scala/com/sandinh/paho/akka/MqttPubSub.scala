package com.sandinh.paho.akka

import java.net.{URLDecoder, URLEncoder}
import akka.actor._
import org.eclipse.paho.client.mqttv3._
import MqttConnectOptions.CLEAN_SESSION_DEFAULT
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

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

  /** TODO support wildcards subscription */
  case class Subscribe(topic: String, ref: ActorRef, qos: Int = 0)

  case class SubscribeAck(subscribe: Subscribe)

  class Message(val topic: String, val payload: Array[Byte])

  //++++ FSM stuff ++++//
  sealed trait S
  case object SDisconnected extends S
  case object SConnected extends S

  //++++ internal FSM event messages ++++//
  private case object Connect
  private case object Connected
  private case object Disconnected

  /** each topic is managed by a Topic actor - which is a child actor of MqttPubSub FSM - with the same name as the topic */
  private class Topic extends Actor {
    private[this] var subscribers = Set.empty[ActorRef]

    def receive = {
      case msg: Message =>
        subscribers foreach (_ ! msg)

      case msg @ Subscribe(_, ref, _) =>
        context watch ref
        subscribers += ref
        ref ! SubscribeAck(msg)

      case Terminated(ref) =>
        subscribers -= ref
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

  private object SubscribeListener extends IMqttActionListener {
    def onSuccess(asyncActionToken: IMqttToken): Unit = {
      logger.info("subscribed to " + asyncActionToken.getTopics.mkString("[", ",", "]"))
    }

    def onFailure(asyncActionToken: IMqttToken, e: Throwable): Unit = {
      logger.error(e)("subscribe failed to " + asyncActionToken.getTopics.mkString("[", ",", "]"))
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
      stashTimeToLive:   FiniteDuration = 1.minute,
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

  //use to stash the pub-sub messages when disconnected
  //note that we do NOT store the sender() in to the stash as in akka.actor.StashSupport#theStash
  private[this] val pubSubStash = ListBuffer.empty[(Deadline, Any)]

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
        case e: Exception =>
          logger.error(e)(s"can't connect to $cfg")
          delayConnect()
      }
      connectCount += 1
      stay()

    case Event(Connected, _) =>
      connectCount = 0
      for ((deadline, x) <- pubSubStash if deadline.hasTimeLeft()) self ! x
      pubSubStash.clear()
      goto(SConnected)

    case Event(x @ (_: Publish | _: Subscribe), _) =>
      if (pubSubStash.length > cfg.stashCapacity) {
        pubSubStash.remove(0, cfg.stashCapacity / 2)
      }
      pubSubStash += Tuple2(Deadline.now + cfg.stashTimeToLive, x)
      stay()
  }

  when(SConnected) {
    case Event(p: Publish, _) =>
      try {
        client.publish(p.topic, p.message())
      } catch {
        case e: Exception => logger.error(e)(s"can't publish to ${p.topic}")
      }
      stay()

    case Event(msg @ Subscribe(topic, ref, qos), _) =>
      val encTopic = urlEnc(topic)
      context.child(encTopic) match {
        case Some(t) => t ! msg
        case None =>
          val t = context.actorOf(Props[Topic], name = encTopic)
          t ! msg
          context watch t
          //FIXME we should store the current qos that client subscribed to topic (in `case Some(t)` above)
          //then, when received a new Subscribe msg if msg.qos > current qos => need re-subscribe
          try {
            client.subscribe(topic, qos, null, SubscribeListener)
          } catch {
            case e: Exception => logger.error(e)(s"can't subscribe to $topic")
          }
      }
      stay()
  }

  whenUnhandled {
    case Event(msg: Message, _) =>
      context.child(urlEnc(msg.topic)) foreach (_ ! msg)
      stay()

    case Event(Terminated(topicRef), _) =>
      try {
        client.unsubscribe(urlDec(topicRef.path.name))
      } catch {
        case e: Exception => logger.error(e)(s"can't unsubscribe from ${topicRef.path.name}")
      }
      stay()

    case Event(Disconnected, _) =>
      delayConnect()
      goto(SDisconnected)
  }

  private def delayConnect(): Unit = {
    val delay = cfg.connectDelay(connectCount)
    logger.info(s"delay $delay before reconnect")
    setTimer("reconnect", Connect, delay)
  }

  initialize()

  self ! Connect
}
