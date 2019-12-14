package com.sandinh.paho.akka
import java.io.File
import sys.process._
import scala.concurrent.duration._
import org.log4s.Logger

trait BrokerHelper {
  protected val logger: Logger
  protected val PATH = "/usr/local/sbin:/usr/sbin:/usr/local/bin:/usr/bin:/bin"
  private def fullPath(bin: String) = PATH.split(':')
    .map(p => s"$p/$bin")
    .find(f => new File(f).exists())
    .getOrElse(throw new RuntimeException(s"not found $bin in path $PATH"))

  protected def startBroker(logPrefix: String = null,
                            port: Int = 1883,
                            useDocker: Boolean = true): Process = {
    val cmd = if (useDocker) {
      s"${fullPath("docker")} run -a stdout -a stderr -p $port:1883 eclipse-mosquitto"
    } else {
      s"${fullPath("mosquitto")} -p $port -v"
    }
    logger.info(cmd)
    val ret = cmd.run(processLogger(logPrefix))
    val wait = 3.seconds
    logger.info(s"waiting $wait for mosquitto start")
    Thread.sleep(wait.toMillis)
    ret
  }

  private def processLogger(prefix: String) =
    if (prefix == null) ProcessLogger(_ => {}) //ignore output and error
    else ProcessLogger(
      s => logger.info(s"$prefix | $s"),
      s => logger.error(s"$prefix | $s")
    )
}
