import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val doobieVersion = "0.5.0-M7"
  val fs2Version = "0.10.0-M6"
  val scodecVersion = "TODO"
  val http4sVersion = "0.18.0-M1"
  val circeVersion = "0.8.0"


  val crossDeps = Def.setting(Seq[ModuleID](
    "com.typesafe" % "config" % "1.3.1",

    "co.fs2" %%% "fs2-core" % fs2Version,

    "com.lihaoyi" % "ammonite" % "1.0.3" cross CrossVersion.full,

    "org.scodec" %%% "scodec-bits" % "1.1.2",
    "org.scodec" %%% "scodec-core" % "1.10.3",
    "org.typelevel" %%% "cats-core" % "1.0.0-MF"
  ))

  val frontendDeps = Def.setting(Seq[ModuleID](
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",

    "co.fs2" %%% "fs2-core" % fs2Version
  ))

  val frontendJSDeps = Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID](
  ))

  val backendDeps = Def.setting(Seq[ModuleID](


    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    // Optional for auto-derivation of JSON codecs
    "io.circe" %% "circe-generic" % "0.9.0-M1",

    // Optional for string interpolation to JSON model
    "io.circe" %% "circe-literal" % "0.9.0-M1",

    "org.http4s" %% "http4s-circe" % http4sVersion,

    "com.chuusai" %% "shapeless" % "2.3.2",

    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-server" % http4sVersion,

    "com.github.nscala-time" %% "nscala-time" % "2.16.0",

    "org.typelevel" %% "cats-effect" % "0.4",

    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io"   % fs2Version,

    "org.tpolecat" %% "doobie-core"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres"   % doobieVersion,
    "org.tpolecat" %% "doobie-specs2"     % doobieVersion,
    "net.postgis" % "postgis-jdbc" % "2.2.1",

    "io.spray" %%  "spray-json" % "1.3.3",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0",

    "org.scodec" %% "scodec-bits" % "1.1.2",
    "org.scodec" %% "scodec-core" % "1.10.3",
    "org.scodec" %% "scodec-stream" % "1.0.1",
    "org.scodec" %% "scodec-protocols" % "1.0.2"
  ))
}
