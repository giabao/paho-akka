package com.sandinh.paho.akka
import sys.process._

trait BrockerHelper {
  protected def startBrocker(name: String): Process = "/usr/sbin/mosquitto -v".run(processLogger(name))

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )
}
