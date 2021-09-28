import sbt._
import sbt.Keys._
import CrossVersion.for3Use2_13

case class AkkaAxis private (version: String) extends VirtualAxis.WeakAxis {
  private def minorVersion = version match {
    case VersionNumber(Seq(2, n, _), _, _) => n
    case _ => sys.error(s"invalid akka version $version")
  }

  val idSuffix = s"_2_$minorVersion"
  val directorySuffix = s"-akka2.$minorVersion"

  // TODO remove `for3Use2_13` when this issue is fixed: https://github.com/akka/akka/issues/30243
  def module(id: String): ModuleID =
    "com.typesafe.akka" %% s"akka-$id" % version cross for3Use2_13

  def depsSetting: Setting[_] = libraryDependencies ++= Seq(
    module("actor"),
    module("testkit") % Test,
  )
}

object AkkaAxis {
  val (akka25, akka26) = (AkkaAxis("2.5.32"), AkkaAxis("2.6.16"))
}
