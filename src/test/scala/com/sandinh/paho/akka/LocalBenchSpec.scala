package com.sandinh.paho.akka

import akka.actor.ActorSystem
import com.sandinh.paho.akka.Docker.Process

class LocalBenchSpec extends BenchBase(
  ActorSystem("L"), "L", "tcp://localhost:2883", 5
) with BrokerHelper {
  protected val logger = org.log4s.getLogger
  private[this] var broker: Process = _
  override def beforeAll() = {
    broker = startBroker(port = 2883)
  }
  override def afterAll() = {
    super.afterAll()
    if (broker != null) broker.destroy()
  }
}
