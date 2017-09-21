import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val doobieVersion = "0.5.0-M7"
  val fs2Version = "0.10.0-M6"
  val fs2httpVersion = "0.2.0-RC1"
  val scodecVersion = "TODO"

  val crossDeps = Def.setting(Seq[ModuleID](
    "com.typesafe" % "config" % "1.3.1",

    "co.fs2" %%% "fs2-core" % fs2Version,

    "org.scodec" %%% "scodec-bits" % "1.1.2",
    "org.scodec" %%% "scodec-core" % "1.10.3",
    "org.typelevel" %%% "cats-core" % "1.0.0-MF"

  ))

  val frontendDeps = Def.setting(Seq[ModuleID](
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",


    "co.fs2" %%% "fs2-core" % fs2Version,

    "fr.hmil" %%% "roshttp" % "2.0.1"
  ))

  val frontendJSDeps = Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID](
  ))

  val backendDeps = Def.setting(Seq[ModuleID](
    "com.chuusai" %% "shapeless" % "2.3.2",
    // "com.spinoco" %% "protocol-http" % fs2httpVersion,
    // "com.spinoco" %% "protocol-websocket" % fs2httpVersion,
    "com.spinoco" %% "fs2-http" % fs2httpVersion,
    "com.spinoco" %% "protocol-http" % "0.1.8",
    "com.spinoco" %% "protocol-websocket" % "0.1.8",

    "com.github.nscala-time" %% "nscala-time" % "2.16.0",

    "org.typelevel" %% "cats-effect" % "0.4",

    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io"   % fs2Version,

    "org.scalactic" %% "scalactic" % "3.0.0",
    "org.scalactic" %% "scalactic" % "3.0.0" % "test",


    "org.tpolecat" %% "doobie-core"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres"   % doobieVersion,
    "org.tpolecat" %% "doobie-specs2"     % doobieVersion,

    "io.spray" %%  "spray-json" % "1.3.3",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0",

    "org.scodec" %% "scodec-bits" % "1.1.2",
    "org.scodec" %% "scodec-core" % "1.10.3",
    "org.scodec" %% "scodec-stream" % "1.0.1",
    "org.scodec" %% "scodec-protocols" % "1.0.2"
  ))
}
