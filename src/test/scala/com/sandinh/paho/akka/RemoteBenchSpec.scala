package com.sandinh.paho.akka

class RemoteBenchSpec extends BenchBase("R") {
  val broker: String = sys.env.getOrElse("REMOTE_BROKER", "tcp://broker.hivemq.com:1883")

  "MqttPubSub" must s"RemoteBench with qos 0" in benchTest(
    broker, 0, 60, 1000
  )

  "MqttPubSub" must s"RemoteBench with qos 1" in benchTest(
    broker, 1, 70, 500
  )

  "MqttPubSub" must s"RemoteBench with qos 2" in benchTest(
    broker, 2, 80, 400
  )
}
