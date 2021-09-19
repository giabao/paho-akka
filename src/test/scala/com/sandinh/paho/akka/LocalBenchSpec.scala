package com.sandinh.paho.akka

import com.sandinh.paho.akka.Docker.Process

class LocalBenchSpec extends BenchBase("L") with BrokerHelper {
  protected val logger = org.log4s.getLogger
  private[this] var broker: Process = _

  override def beforeAll(): Unit = {
    broker = startBroker(port = 2883)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    if (broker != null) broker.destroy()
  }

  "MqttPubSub" must s"LocalBench" in benchTest(
    "tcp://localhost:2883", 0, 5, 10000
  )
}
