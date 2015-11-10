organization := "com.sandinh"
name := "paho-akka"

version := "1.2.0"

scalaVersion := "2.11.7"
//TODO re-enable cross when scala release version 2.12
//crossScalaVersions := Seq(scalaVersion.value, "2.12.0")

scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-deprecation", "-feature", "-Xfuture", //"–Xverify", "-Xcheck-null",
  "-Ywarn-dead-code", "-Ydead-code", "-Yinline-warnings" //"-Yinline", "-Ystatistics",
)

resolvers += "Paho Releases" at "https://repo.eclipse.org/content/repositories/paho-releases"

libraryDependencies ++= Seq(
  "org.eclipse.paho"  % "org.eclipse.paho.client.mqttv3"  % "1.0.2",
  "com.typesafe.akka" %% "akka-actor"                     % "2.4.0",
  "org.log4s"         %% "log4s"                          % "1.2.1",
  "org.scalatest"     %% "scalatest"      % "2.2.5"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % "2.4.0"   % Test,
  "ch.qos.logback"    % "logback-classic" % "1.1.3"   % Test
)

//misc - to mute intellij warning when load sbt project
dependencyOverrides ++= Set(
  "org.scala-lang.modules"  %% "scala-xml"    % "1.0.5",
  "org.scala-lang"          % "scala-reflect" % scalaVersion.value
)
