package com.sandinh.paho.akka
import sys.process._

trait BrockerHelper {
  protected def startBrocker(name: String = null): Process = {
    val logger =
      if (name == null) ProcessLogger(_ => {}) //ignore output and error
      else processLogger(name)
    "/usr/sbin/mosquitto -v".run(logger)
  }

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )
}
