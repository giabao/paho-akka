package com.sandinh.paho.akka
import akka.Done

import java.io.{File, FileWriter}
import sys.process._
import scala.concurrent.duration._
import org.log4s.Logger

import java.nio.file.Files
import scala.concurrent.{Await, Future, Promise}
import scala.util.Using

trait BrokerHelper {
  protected val logger: Logger
  protected val PATH = "/usr/local/sbin:/usr/sbin:/usr/local/bin:/usr/bin:/bin"
  private def fullPath(bin: String) = PATH.split(':')
    .map(p => s"$p/$bin")
    .find(f => new File(f).exists())
    .getOrElse(throw new RuntimeException(s"not found $bin in path $PATH"))

  private val mosquittoVersion = "2.0.12"
  private val mosquittoImg = s"eclipse-mosquitto:$mosquittoVersion"

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

    val docker = fullPath("docker")
    assert(s"$docker pull $mosquittoImg".! == 0)

    val cmd = s"$docker run -a stdout -a stderr -p $port:1883 -v $confFile:/mosquitto/config/mosquitto.conf $mosquittoImg"

    logger.info(cmd)
    val (log, fut) = processLogger(logPrefix, _.endsWith(s"mosquitto version $mosquittoVersion running"))
    val ret = cmd.run(log)
    logger.info(s"waiting $wait for mosquitto start")
    assert(Await.result(fut, wait) == Done)
    ret
  }

  private def processLogger(prefix: String, doneWhen: String => Boolean): (ProcessLogger, Future[Done]) = {
    val p = Promise[Done]()
    def onStd(s: String, log: String => Unit): Unit = {
      if (doneWhen(s)) p.success(Done)
      if (prefix != null) log(s"$prefix | $s")
    }
    (ProcessLogger(
      onStd(_, logger.info(_)),
      onStd(_, logger.error(_))
    ), p.future)
  }
}
