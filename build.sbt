def scalatest(modules: String*) = modules.map { m =>
  "org.scalatest" %% s"scalatest$m" % "3.2.10" % Test
}

lazy val depsSetting = libraryDependencies ++= Seq(
  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5",
  "org.slf4j" % "slf4j-api" % "1.7.32",
  scalaColCompat % Provided, // to use @nowarn & silencer
  scalaColCompat % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.6" % Test,
) ++ akka("actor", "testkit" -> Test).value ++
  scalatest("-flatspec", "-shouldmatchers", "-wordspec")

lazy val `paho-akka` = projectMatrix
  .akkaAxis(akka25, Seq(scala211, scala212, scala213))
  .akkaAxis(akka26, Seq(scala212, scala213, scala3))
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
