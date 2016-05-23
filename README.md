paho-akka
=========
[![Build Status](https://travis-ci.org/giabao/paho-akka.svg)](https://travis-ci.org/giabao/paho-akka)

## What?
This is a Publish Subscribe library for [akka](http://akka.io/) to pub/sub to a MQTT server - ex [mosquitto](http://mosquitto.org/).
paho-akka use [paho](https://eclipse.org/paho/) as the underlying MQTT client.

We, at http://sandinh.com, use paho-akka to replace [Distributed Publish Subscribe in Cluster](http://doc.akka.io/docs/akka/2.3.8/contrib/distributed-pub-sub.html)

## Install?
paho-akka is on [Maven Center](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.sandinh%22%20paho-akka)

## How to use

```scala
val pubsub = actorOf(Props(classOf[MqttPubSub], PSConfig(
    brokerUrl = "tcp://test.mosquitto.org:1883", //all params is optional except brokerUrl
    userName = null,
    password = null,
    //messages received when disconnected will be stash. Messages isOverdue after stashTimeToLive will be discard
    stashTimeToLive = 1.minute,
    stashCapacity = 8000, //stash messages will be drop first haft elems when reach this size
    reconnectDelayMin = 10.millis, //for fine tuning re-connection logic
    reconnectDelayMax = 30.seconds
)))

pubsub ! new Publish(topic, payload)

class SubscribeActor extends Actor {
  pubsub ! Subscribe(topic, self)

  def receive = {
    case SubscribeAck(Subscribe(`topic`, `self`, _), fail) =>
      if (fail.isEmpty) context become ready
      else logger.error(fail.get, s"Can't subscribe to $topic")
  }

  def ready: Receive = {
    case msg: Message => ...
  }
}
```

## Changelogs
we use [Semantic Versioning](http://semver.org/)

##### v1.3.0-SNAPSHOT
+ update akka 2.4.6, log4s 1.3.0
+ cross compile to scala 2.11.8, 2.12.0-M4
+ update sbt 0.13.11, sbt-scalariform 1.6.0, sbt-sonatype 1.1
+ travis test for oraclejdk8 & openjdk8
+ fix #2 (PR #7) resubscribe after reconnected
+ PSConfig.stashTimeToLive's type changed from FiniteDuration to Duration. Now it can be Duration.Inf
+ breaking change: `case class SubscribeAck(subscribe: Subscribe)` is changed to
 `case class SubscribeAck(subscribe: Subscribe, fail: Option[Throwable])`
+ add a helper Dockerfile for local testing using docker

##### v1.2.0
+ update akka 2.4.0 (drop support java7, scala 2.10.x)

##### v1.1.2
+ delay (re)connect when client.connect throws Exception 
+ update scala 2.10.5 -> 2.10.6 (keep 2.11.7)
+ update akka 2.3.14, log4s 1.2.1
+ update sbt-sonatype 1.0

##### v1.1.1
+ fix re-connect issue: When re-connect we should make MqttPubSub FSM goto SDisconnected state
+ add try-catch when calling underlying client's method in MqttPubSub FSM

##### v1.1.0
+ cross compile to scala 2.11.7, 2.10.5
+ use log4s instead of scala-logging
+ test with logback instead of log4j2
+ try catch when call underlying mqtt client.publish

##### v1.0.3
+ update scala 2.11.7, org.eclipse.paho.client.mqttv3 1.0.2, akka-actor 2.3.12
+ support cleanSession MqttConnectOptions
+ log subscribe actions

##### v1.0.2
only update scala 2.11.5

##### v1.0.1
+ update akka 2.3.8
+ add ByteArrayConverters util
+ MqttPubSub FSM: add a call `initialize()` & add/change some logging statements

##### v1.0.0
first stable release

## Licence
This software is licensed under the Apache 2 license:
http://www.apache.org/licenses/LICENSE-2.0

Copyright 2014 Sân Đình (http://sandinh.com)
