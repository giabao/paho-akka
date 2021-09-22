package com.sandinh.paho.akka
import akka.Done

import java.io.{File, FileWriter}
import sys.process._
import Docker.Process
import org.slf4j.Logger

import scala.concurrent.duration._
import java.nio.file.Files
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Random, Using}

trait BrokerHelper {
  protected val logger: Logger

  private val mosquittoVersion =
    sys.env.getOrElse("MOSQUITTO_VERSION", "2.0.12")
  private val mosquittoImg = s"eclipse-mosquitto:$mosquittoVersion"

  protected def startBroker(
      logPrefix: String = "",
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

    Docker.pull(mosquittoImg)
    val ret = Docker.run(
      mosquittoImg,
      s"-a stdout -a stderr -p $port:1883",
      s"-v $confFile:/mosquitto/config/mosquitto.conf"
    )(
      logger,
      logPrefix,
      _.endsWith(s"mosquitto version $mosquittoVersion running")
    )

    logger.info(s"waiting $wait for mosquitto start")
    assert(Await.result(ret.donFuture, wait) == Done)
    ret
  }
}

object Docker {
  case class Process(name: String, donFuture: Future[Done]) {
    def stop(): Int = s"$docker stop $name".!
    def destroy(): Int = stop()
  }

  protected val PATH = "/usr/local/sbin:/usr/sbin:/usr/local/bin:/usr/bin:/bin"
  private def fullPath(bin: String) = PATH
    .split(':')
    .map(p => s"$p/$bin")
    .find(f => new File(f).exists())
    .getOrElse(throw new RuntimeException(s"not found $bin in path $PATH"))

  private lazy val docker = fullPath("docker")

  def pull(img: String): Unit = assert(s"$docker pull $img".! == 0)

  def run(
      img: String,
      args0: String*
  )(logger: Logger, logPrefix: String, doneWhen: String => Boolean): Process = {
    def info(s: String): Unit = logger.info(s"$logPrefix | $s")
    def warn(s: String): Unit = logger.warn(s"$logPrefix | $s")

    val (name, cmd) = {
      val i = args0.indexWhere(a => a == "--name" || a.startsWith("--name "))
      val name =
        if (i != -1) args0(i + 1)
        else "n" + Random.nextLong()
      val nameArg = if (i != -1) Nil else Seq("--name", name)
      val args = Seq(docker, "run") ++ args0 ++ nameArg :+ img
      (name, args.mkString(" "))
    }

    info(cmd)

    val promise = Promise[Done]()
    def onStd(log: String => Unit)(s: String): Unit = {
      if (doneWhen(s)) {
        log("Done!")
        promise.success(Done)
      }
      log(s)
    }
    cmd.run(ProcessLogger(onStd(info), onStd(warn)))
    Process(name, promise.future)
  }
}
