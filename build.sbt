organization := "com.sandinh"
name := "paho-akka"
version := "1.0.0"

scalaVersion := "2.11.4"
crossScalaVersions := Seq("2.11.4", "2.10.4")

scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-deprecation", "-feature",
  "-Xfuture", //"–Xverify", "-Xcheck-null",
  "-Ywarn-dead-code", "-Ydead-code", "-Yinline-warnings" //"-Yinline", "-Ystatistics",
)

resolvers += "Paho Releases"     at "https://repo.eclipse.org/content/repositories/paho-releases"

libraryDependencies ++= {
  val paho  = "1.0.1"
  val ak    = "2.3.7"
  val slog  = "3.1.0"
  val stest = "2.2.2"
  Seq(
    "org.eclipse.paho"            % "org.eclipse.paho.client.mqttv3"  % paho,
    "com.typesafe.akka"           %% "akka-actor"                     % ak,
    "com.typesafe.scala-logging"  %% "scala-logging"                  % slog,
    "com.typesafe.akka"           %% "akka-testkit"                   % ak      % "test",
    "org.scalatest"               %% "scalatest"                      % stest   % "test"
  ) ++ Seq("log4j-slf4j-impl", "log4j-core", "log4j-api").map(
    "org.apache.logging.log4j"    % _                % "2.1"   % "test"
  )
}
