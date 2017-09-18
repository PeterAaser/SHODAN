import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val doobieVersion = "0.4.1"
  val fs2Version = "0.10.0-M6"

  val crossDeps = Def.setting(Seq[ModuleID](
    "com.typesafe" % "config" % "1.3.1",

    "co.fs2" %%% "fs2-core" % fs2Version,

    "org.scodec" %%% "scodec-bits" % "1.1.2",
    "org.scodec" %%% "scodec-core" % "1.10.3",
    "org.typelevel" %%% "cats" % "0.9.0"

  ))

  val frontendDeps = Def.setting(Seq[ModuleID](
    "com.github.japgolly.scalacss" %%% "core" % "0.5.0",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.0",
    "org.singlespaced" %%% "scalajs-d3" % "0.3.4",
    "com.lihaoyi" %%% "upickle" % "0.4.3",

    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",


    "co.fs2" %%% "fs2-core" % fs2Version,

    "fr.hmil" %%% "roshttp" % "2.0.1"
  ))

  val frontendJSDeps = Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID](
  ))

  val backendDeps = Def.setting(Seq[ModuleID](
    "com.spinoco" %% "protocol-http" % "0.1.8",
    "com.spinoco" %% "protocol-websocket" % "0.1.8",
    "com.spinoco" %% "fs2-http" % "0.1.7",

    "com.github.nscala-time" %% "nscala-time" % "2.16.0",

    "org.typelevel" %% "cats-effect" % "0.4",

    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io"   % fs2Version,

    "org.scalactic" %% "scalactic" % "3.0.0",
    "org.scalactic" %% "scalactic" % "3.0.0" % "test",

    "org.tpolecat" %% "doobie-core-cats"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres-cats"   % doobieVersion,
    "org.tpolecat" %% "doobie-specs2-cats"     % doobieVersion,

    "io.spray" %%  "spray-json" % "1.3.3",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0",

    "org.scodec" %% "scodec-bits" % "1.1.2",
    "org.scodec" %% "scodec-core" % "1.10.3",
    "org.scodec" %% "scodec-stream" % "1.0.1",
    "org.scodec" %% "scodec-protocols" % "1.0.2"
  ))
}
