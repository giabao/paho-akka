package com.sandinh.paho.akka

import java.net.{URLDecoder, URLEncoder}
import akka.actor._
import com.typesafe.scalalogging.StrictLogging
import org.eclipse.paho.client.mqttv3._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object MqttPubSub extends StrictLogging {
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
      logger.error(s"connection lost", cause)
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

    def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit = {
      logger.error("connect failed", exception)
      owner ! Disconnected
    }
  }

  private class SubscribeListener(owner: ActorRef) extends IMqttActionListener {
    def onSuccess(asyncActionToken: IMqttToken): Unit = {
      asyncActionToken.getTopics.foreach(topic => logger.info(s"subscribe $topic success."))
    }

    def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit = {
      val topicList = asyncActionToken.getTopics.mkString(",")
      logger.error(s"subscribe to $topicList failed", exception)
    }
  }

  //ultilities
  @inline private def urlEnc(s: String) = URLEncoder.encode(s, "utf-8")
  @inline private def urlDec(s: String) = URLDecoder.decode(s, "utf-8")

  /** @param brokerUrl ex tcp://test.mosquitto.org:1883
    * @param userName nullable
    * @param password nullable
    * @param cleanSession Sets whether the client and server should remember state across restarts and reconnects.
    * @param stashTimeToLive messages received when disconnected will be stash.
    * Messages isOverdue after stashTimeToLive will be discard. See also `stashCapacity`
    * @param stashCapacity pubSubStash will be drop first haft elems when reach this size
    * @param reconnectDelayMin when received Disconnected event, we will first delay reconnectDelayMin to try Connect.
    * + if connect success => we reinit connectCount
    * + else => ConnListener.onFailure will send Disconnected to this FSM =>
    * we re-schedule Connect with {{{delay = reconnectDelayMin * 2^connectCount}}}
    * @param reconnectDelayMax max delay to retry connecting */
  case class PSConfig(
      brokerUrl:         String,
      userName:          String         = null,
      password:          String         = null,
      cleanSession:      Boolean        = false,
      stashTimeToLive:   FiniteDuration = 1.minute,
      stashCapacity:     Int            = 8000,
      reconnectDelayMin: FiniteDuration = 10.millis,
      reconnectDelayMax: FiniteDuration = 30.seconds
  ) {

    //pre-calculate the max of connectCount that: reconnectDelayMin * 2^connectCountMax ~ reconnectDelayMax
    val connectCountMax = Math.floor(Math.log(reconnectDelayMax / reconnectDelayMin) / Math.log(2)).toInt

    def connectDelay(connectCount: Int) =
      if (connectCount >= connectCountMax) reconnectDelayMax
      else reconnectDelayMin * (1L << connectCount)
  }
}

import MqttPubSub._

/** Notes:
  * 1. MqttClientPersistence will be set to null. @see org.eclipse.paho.client.mqttv3.MqttMessage#setQos(int)
  * 2. MQTT client will auto-reconnect */
class MqttPubSub(cfg: PSConfig) extends FSM[S, Unit] with StrictLogging {
  //setup MqttConnectOptions
  private[this] val conOpt = {
    val opt = new MqttConnectOptions //conOpt.cleanSession == true
    if (cfg.userName != null) opt.setUserName(cfg.userName)
    if (cfg.password != null) opt.setPassword(cfg.password.toCharArray)
    opt.setCleanSession(cfg.cleanSession)
    opt
  }
  //setup MqttAsyncClient without MqttClientPersistence
  private[this] val client = {
    val c = new MqttAsyncClient(cfg.brokerUrl, MqttAsyncClient.generateClientId(), null)
    c.setCallback(new PubSubMqttCallback(self))
    c
  }
  //setup Connnection IMqttActionListener
  private[this] val conListener = new ConnListener(self)

  //setup Connnection IMqttActionListener
  private[this] val subListener = new SubscribeListener(self)

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
      client.connect(conOpt, null, conListener)
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
      client.publish(p.topic, p.message())
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
          client.subscribe(topic, qos, null, subListener)
      }
      stay()
  }

  whenUnhandled {
    case Event(msg: Message, _) =>
      context.child(urlEnc(msg.topic)) foreach (_ ! msg)
      stay()

    case Event(Terminated(topicRef), _) =>
      client.unsubscribe(urlDec(topicRef.path.name))
      stay()

    case Event(Disconnected, _) =>
      val delay = cfg.connectDelay(connectCount)
      logger.info(s"delay $delay before reconnect")
      setTimer("reconnect", Connect, delay)
      stay()
  }

  override def postStop(): Unit = {
    client.disconnectForcibly()
    super.postStop()
  }

  initialize()

  self ! Connect
}
