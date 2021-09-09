package com.sandinh.paho.akka
import java.io.{File, FileWriter}
import sys.process._
import scala.concurrent.duration._
import org.log4s.Logger

import java.nio.file.Files
import scala.util.Using

trait BrokerHelper {
  protected val logger: Logger
  protected val PATH = "/usr/local/sbin:/usr/sbin:/usr/local/bin:/usr/bin:/bin"
  private def fullPath(bin: String) = PATH.split(':')
    .map(p => s"$p/$bin")
    .find(f => new File(f).exists())
    .getOrElse(throw new RuntimeException(s"not found $bin in path $PATH"))

  protected def startBroker(
    logPrefix: String = null,
    port: Int = 1883,
    wait: FiniteDuration = 3.seconds
  ): Process = {
    val confFile = Files.createTempFile("paho", ".conf").toFile
    confFile.deleteOnExit()
    Using.resource(
      new FileWriter(confFile.getAbsolutePath)
    )(
      _.write("""
         |listener 1883
         |allow_anonymous true
         |""".stripMargin)
    )
    val cmd = s"${fullPath("docker")} run -a stdout -a stderr -p $port:1883 -v $confFile:/mosquitto/config/mosquitto.conf eclipse-mosquitto:2"
    logger.info(cmd)
    val ret = cmd.run(processLogger(logPrefix))
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
