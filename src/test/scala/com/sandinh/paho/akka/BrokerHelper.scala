package com.sandinh.paho.akka
import sys.process._

trait BrokerHelper {
  protected def startBroker(logPrefix: String = null, port: Int = 1883): Process = {
    val brokerFile = {
      // use /usr/local/sbin/mosquitto (if it exists) before /usr/sbin/mosquitto
      if (scala.reflect.io.File(scala.reflect.io.Path("/usr/local/sbin/mosquitto")).exists)
        "/usr/local/sbin/mosquitto"
      else
        "/usr/sbin/mosquitto"
    }
    if (logPrefix == null) {
      s"$brokerFile -p $port".run(ProcessLogger(_ => {})) //ignore output and error
    } else {
      s"$brokerFile -p $port -v".run(processLogger(logPrefix))
    }
  }

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )
}
