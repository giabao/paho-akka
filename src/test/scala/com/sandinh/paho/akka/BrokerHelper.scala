package com.sandinh.paho.akka
import java.io.File
import sys.process._

trait BrokerHelper {
  protected val PATH = "/usr/local/sbin:/usr/sbin"

  protected def startBroker(logPrefix: String = null, port: Int = 1883): Process = {
    val broker = PATH.split(':')
      .map(p => s"$p/mosquitto")
      .find(f => new File(f).exists())
      .getOrElse(throw new RuntimeException(s"not found mosquitto in path $PATH"))
    if (logPrefix == null) {
      s"$broker -p $port".run(ProcessLogger(_ => {})) //ignore output and error
    } else {
      s"$broker -p $port -v".run(processLogger(logPrefix))
    }
  }

  private def processLogger(prefix: String) = ProcessLogger(
    s => println(s"OUT | $prefix | $s"),
    s => println(s"ERR | $prefix | $s")
  )
}
