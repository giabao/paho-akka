## Changelog
we use [Semantic Versioning](http://semver.org/)

##### v1.4.0
+ update akka 2.5.4, scala 2.11.11 & 2.12.3, paho.client.mqttv3 1.2.0, log4s 1.3.6
+ building settings change:
  - update sbt 1.0.1, sbt-sonatype 2.0, sbt-pgp 1.1.0
  - use sbt-coursier
  - use sbt-scalafmt-coursier instead of sbt-scalariform
+ pull #13 - Added configurable client id in PSConfig. If left null, the code falls back to a generated ID (compatible with v1.3.x)
  breaking change: add `PSConfig._clientId`

##### v1.3.0
+ update akka 2.4.6, log4s 1.3.0
+ cross compile to scala 2.11.8, 2.12.0-M4
+ update sbt 0.13.11, sbt-scalariform 1.6.0, sbt-sonatype 1.1
+ travis test for oraclejdk8 & openjdk8
+ fix #2 (PR #7) resubscribe after reconnected
+ PSConfig.stashTimeToLive's type changed from FiniteDuration to Duration. Now it can be Duration.Inf
+ breaking change: `case class SubscribeAck(subscribe: Subscribe)` is changed to
 `case class SubscribeAck(subscribe: Subscribe, fail: Option[Throwable])`
+ move (breaking change) MqttPubSub's inner classes to separated files/classes:
  `com.sandinh.paho.akka.MqttPubSub.PSConfig` -> `com.sandinh.paho.akka.PSConfig`
  similar for `Message`, `Publish`, `Subscribe`, `SubscribeAck`
+ rename States: S -> PSState, SDisconnected -> DisconnectedState, SConnected -> ConnectedState.
  Those types should be internal used, so will not cause incompatible changes
+ add convenient method `Publish.apply` so, instead of `new Publish(..)` we can write `Publish(..)`
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
