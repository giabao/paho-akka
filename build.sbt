organization := "com.sandinh"

name := "paho-akka"

version := "1.0.2"

scalaVersion := "2.11.5"

scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-deprecation", "-feature", "-Xfuture", //"–Xverify", "-Xcheck-null",
  "-Ywarn-dead-code", "-Ydead-code", "-Yinline-warnings" //"-Yinline", "-Ystatistics",
)

resolvers += "Paho Releases"     at "https://repo.eclipse.org/content/repositories/paho-releases"

libraryDependencies ++= Seq(
  "org.eclipse.paho"            % "org.eclipse.paho.client.mqttv3"  % "1.0.1",
  "com.typesafe.akka"           %% "akka-actor"                     % "2.3.8",
  "com.typesafe.scala-logging"  %% "scala-logging"                  % "3.1.0"
)

libraryDependencies ++= Seq(
  "org.scalatest"     %% "scalatest"    % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.8"
) ++ Seq("core", "api", "slf4j-impl").map(s =>
  "org.apache.logging.log4j" % s"log4j-$s" % "2.1"
) map (_ % "test")
