organization := "com.sandinh"
name := "paho-akka"

version := "1.6.0"

scalaVersion := "2.13.1"
crossScalaVersions := Seq("2.12.10", "2.13.1")

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature")
scalacOptions ++= (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 12)) => Seq("-target:jvm-1.8")
  case _ => Nil
})

//for test against multiple paho client versions
//see https://github.com/eclipse/paho.mqtt.java/issues/405
val pahoVersion = Option(System.getenv("PAHO_CLIENT_VERSION")).getOrElse("1.2.2")
val akkaVersion = "2.6.1"
libraryDependencies ++= Seq(
  "org.eclipse.paho"  % "org.eclipse.paho.client.mqttv3" % pahoVersion,
  "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
  "org.log4s"         %% "log4s"          % "1.8.2",
  "org.scalatest"     %% "scalatest"      % "3.1.0"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % akkaVersion % Test,
  "ch.qos.logback"    % "logback-classic" % "1.2.3"   % Test
)
