organization := "com.sandinh"
name := "paho-akka"

version := "1.3.0-SNAPSHOT"

scalaVersion := "2.11.8"
crossScalaVersions := Seq("2.11.8", "2.12.0-M4")

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-target:jvm-1.8")
scalacOptions ++= (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ybackend:GenBCode")
  case _ => Nil
})

resolvers += "Paho Releases" at "https://repo.eclipse.org/content/repositories/paho-releases"

libraryDependencies ++= Seq(
  "org.eclipse.paho"  % "org.eclipse.paho.client.mqttv3"  % "1.0.2",
  "com.typesafe.akka" %% "akka-actor"                     % "2.4.6",
  "org.log4s"         %% "log4s"                          % "1.3.0",
  "org.scalatest"     %% "scalatest"      % "2.2.6"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % "2.4.6"   % Test,
  "ch.qos.logback"    % "logback-classic" % "1.1.7"   % Test
)
