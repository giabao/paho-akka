paho-akka
=========
[![CI](https://github.com/giabao/paho-akka/actions/workflows/sd-devops.yml/badge.svg)](https://github.com/giabao/paho-akka/actions/workflows/sd-devops.yml)
[![Known Vulnerabilities](https://snyk.io/test/github/mramshaw/paho-akka/badge.svg?style=plastic&targetFile=build.sbt)](https://snyk.io/test/github/mramshaw/paho-akka?style=plastic&targetFile=build.sbt)

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

## Changelog
see [CHANGES.md]

## Licence
This software is licensed under the Apache 2 license:
http://www.apache.org/licenses/LICENSE-2.0

Copyright 2014 Sân Đình (http://sandinh.com)
