organization := "com.sandinh"
name := "paho-akka"

version := "1.5.1"

scalaVersion := "2.11.12"
crossScalaVersions := Seq("2.11.12", "2.12.15", "2.13.6")

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature")
scalacOptions ++= (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ybackend:GenBCode", "-target:jvm-1.8")
  case Some((2, 12)) => Seq("-target:jvm-1.8")
  case _ => Nil
})

val pahoVersion = "1.2.5"
val akkaVersion = "2.5.32"
libraryDependencies ++= Seq(
  "org.eclipse.paho"  % "org.eclipse.paho.client.mqttv3" % pahoVersion,
  "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
  "org.slf4j"         % "slf4j-api"       % "1.7.32",
  "org.scalatest"     %% "scalatest"      % "3.2.9"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % akkaVersion % Test,
  "ch.qos.logback"    % "logback-classic" % "1.2.5"   % Test,
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0" % Test,
)
