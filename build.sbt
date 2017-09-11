organization := "com.sandinh"
name := "paho-akka"

version := "1.4.0"

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.11.11", "2.12.3")

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-target:jvm-1.8")
scalacOptions ++= (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ybackend:GenBCode")
  case _ => Nil
})

resolvers += "Paho Releases" at "https://repo.eclipse.org/content/repositories/paho-releases"

libraryDependencies ++= Seq(
  "org.eclipse.paho"  % "org.eclipse.paho.client.mqttv3"  % Option(System.getenv("PAHO_CLIENT_VERSION")).getOrElse("1.1.1"),
  "com.typesafe.akka" %% "akka-actor"                     % "2.5.4",
  "org.log4s"         %% "log4s"                          % "1.3.6",
  "org.scalatest"     %% "scalatest"      % "3.0.4"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % "2.5.4"   % Test,
  "ch.qos.logback"    % "logback-classic" % "1.2.3"   % Test
)
