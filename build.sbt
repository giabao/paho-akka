import CrossVersion.for3Use2_13

inThisBuild(Seq(
  organization := "com.sandinh",
  version := "1.5.1",
))

lazy val akka25 = ConfigAxis("_2_5", "-akka2.5")
lazy val akka26 = ConfigAxis("_2_6", "-akka2.6")
lazy val akkaVersion = settingKey[String]("akkaVersion")

lazy val depsSetting = libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % akkaVersion.value        cross for3Use2_13,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % Test cross for3Use2_13,
  "org.eclipse.paho"       % "org.eclipse.paho.client.mqttv3" % "1.2.5",
  "org.slf4j"              % "slf4j-api"                      % "1.7.32",
  "org.scalatest"          %% "scalatest"                     % "3.2.9"   % Test,
  "ch.qos.logback"         % "logback-classic"                % "1.2.6"   % Test,
  "org.scala-lang.modules" %% "scala-collection-compat"       % "2.5.0"   % Test,
)

lazy val scalacSetting = scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-deprecation", "-feature"
) ++ (CrossVersion.scalaApiVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ybackend:GenBCode", "-target:jvm-1.8")
  case Some((2, 12)) => Seq("-target:jvm-1.8")
  case _ => Nil
})

lazy val `paho-akka` = projectMatrix
  .customRow(
    scalaVersions = Seq("2.11.12", "2.12.15", "2.13.6"),
    axisValues = Seq(akka25, VirtualAxis.jvm),
    Seq(
      akkaVersion := "2.5.32",
      moduleName := name.value + "_2_5",
      depsSetting,
      scalacSetting
    ),
  )
  .customRow(
    scalaVersions = Seq("2.12.15", "2.13.6", "3.0.2"),
    axisValues = Seq(akka26, VirtualAxis.jvm),
    Seq(
      akkaVersion := "2.6.16",
      moduleName := name.value,
      depsSetting,
      scalacSetting
    ),
  )
