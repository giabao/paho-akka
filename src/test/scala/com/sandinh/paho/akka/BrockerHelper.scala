package com.sandinh.paho.akka
import sys.process._

trait BrockerHelper {
  protected def startBrocker(logPrefix: String = null, port: Int = 1883): Process = {
    if (logPrefix == null) {
      s"/usr/sbin/mosquitto -p $port".run(ProcessLogger(_ => {})) //ignore output and error
    } else {
      s"/usr/sbin/mosquitto -p $port -v".run(processLogger(logPrefix))
    }
  }

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )
}
