package com.sandinh.paho.akka

import akka.actor.ActorSystem

class RemoteBenchSpec extends BenchBase(
  ActorSystem("R"), "R", "tcp://test.mqtt.ohze.net:1883", 40
)
