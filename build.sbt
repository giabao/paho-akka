organization := "com.sandinh"
name := "paho-akka"

version := "1.5.0"

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.11.11", "2.12.3")

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-target:jvm-1.8")
scalacOptions ++= (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ybackend:GenBCode")
  case _ => Nil
})

resolvers += "Paho Releases" at "https://repo.eclipse.org/content/repositories/paho-releases"

//for test against multiple paho client versions
//see https://github.com/eclipse/paho.mqtt.java/issues/405
lazy val pahoLib = {
  val lib = "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3"
  System.getenv("PAHO_CLIENT_VERSION") match {
    case null => lib % "1.1.1"
    case v if !v.contains("-SNAPSHOT") => lib % v
    case s =>
      val i = s.indexOf('/')
      val v = s.substring(0, i)
      val fileName = s.substring(i + 1)
      lib % v from s"https://repo.eclipse.org/content/repositories/paho-snapshots/org/eclipse/paho/org.eclipse.paho.client.mqttv3/$v/org.eclipse.paho.client.mqttv3-$fileName.jar"
  }
}

libraryDependencies ++= Seq(
  pahoLib,
  "com.typesafe.akka" %% "akka-actor"     % "2.5.4",
  "org.log4s"         %% "log4s"          % "1.3.6",
  "org.scalatest"     %% "scalatest"      % "3.0.4"   % Test,
  "com.typesafe.akka" %% "akka-testkit"   % "2.5.4"   % Test,
  "ch.qos.logback"    % "logback-classic" % "1.2.3"   % Test
)
