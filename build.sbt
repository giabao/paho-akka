import CrossVersion.for3Use2_13

lazy val akka25 = ConfigAxis("_2_5", "-akka2.5")
lazy val akka26 = ConfigAxis("_2_6", "-akka2.6")
lazy val akkaVersion = settingKey[String]("akkaVersion")

lazy val depsSetting = libraryDependencies ++= Seq(
  // TODO remove `for3Use2_13` when this issue is fixed: https://github.com/akka/akka/issues/30243
  "com.typesafe.akka" %% "akka-actor" % akkaVersion.value cross for3Use2_13,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value % Test cross for3Use2_13,
  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5",
  "org.slf4j" % "slf4j-api" % "1.7.32",
  "org.scalatest" %% "scalatest" % "3.2.9" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.6" % Test,
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0" % Test,
)

lazy val `paho-akka` = projectMatrix
  .customRow(
    scalaVersions = Seq(scala211, scala212, scala213),
    axisValues = Seq(akka25, VirtualAxis.jvm),
    Seq(
      akkaVersion := "2.5.32",
      moduleName := name.value + "_2_5",
    ),
  )
  .customRow(
    scalaVersions = Seq(scala212, scala213, scala3),
    axisValues = Seq(akka26, VirtualAxis.jvm),
    Seq(
      akkaVersion := "2.6.16",
      moduleName := name.value,
    ),
  )
  .settings(depsSetting)

inThisBuild(
  Seq(
    versionScheme := Some("semver-spec"),
    developers := List(
      Developer(
        "thanhbv",
        "Bui Viet Thanh",
        "thanhbv@sandinh.net",
        url("https://sandinh.com")
      ),
      Developer(
        "mramshaw",
        "Martin Ramshaw",
        "mramshaw@alumni.concordia.ca",
        url("https://github.com/mramshaw")
      ),
      Developer(
        "crawford42",
        "Robert Crawford",
        "crawford@kloognome.com",
        url("http://www.kloognome.com")
      ),
    ),
  )
)
