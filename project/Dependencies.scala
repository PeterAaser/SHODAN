import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val udashVersion = "0.4.0"
  val udashJQueryVersion = "1.0.0"
  val logbackVersion = "1.1.3"
  val jettyVersion = "9.3.11.v20160721"
  val doobieVersion = "0.4.0"

  val crossDeps = Def.setting(Seq[ModuleID](
    "io.udash" %%% "udash-core-shared" % udashVersion,
    "io.udash" %%% "udash-rpc-shared" % udashVersion,
    "io.spray" %%  "spray-json" % "1.3.3",
    "com.typesafe" % "config" % "1.3.1",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0",
    "com.quantifind" %% "wisp" % "0.0.4"
  ))

  val frontendDeps = Def.setting(Seq[ModuleID](
    "io.udash" %%% "udash-core-frontend" % udashVersion,
    "io.udash" %%% "udash-jquery" % udashJQueryVersion,
    "io.udash" %%% "udash-rpc-frontend" % udashVersion,
    "com.github.japgolly.scalacss" %%% "core" % "0.5.0",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.0"
  ))

  val frontendJSDeps = Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID](
  ))

  val backendDeps = Def.setting(Seq[ModuleID](
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "io.udash" %% "udash-rpc-backend" % udashVersion,
    "org.eclipse.jetty.websocket" % "websocket-server" % jettyVersion,

    "com.typesafe.akka" %% "akka-actor" % "2.4.11",
    "com.typesafe.akka" % "akka-stream_2.11" % "2.4.11",
    "co.fs2" %% "fs2-core" % "0.9.0",
    "co.fs2" %% "fs2-io" % "0.9.0",
    "org.scalactic" %% "scalactic" % "3.0.0",
    "org.scalactic" %% "scalactic" % "3.0.0" % "test",
    "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test",
    "org.tpolecat" % "doobie-core-cats_2.11" % "0.3.1-M1",
    "org.tpolecat" % "doobie-postgres-cats_2.11" % "0.3.1-M1",
    "org.scodec" %% "scodec-bits" % "1.1.2",
    "org.scodec" %% "scodec-protocols" % "1.0.2",
    "org.scodec" %% "scodec-stream" % "1.0.1",
    "com.chuusai" %% "shapeless" % "2.3.2",
    "com.typesafe.akka" % "akka-http" % "3.0.0-RC1",
    "com.lihaoyi" % "upickle_2.11" % "0.4.4",
    "org.scalaz" %% "scalaz-core" % "7.2.8",
    "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    "org.tpolecat" %% "doobie-core-cats"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres-cats"   % doobieVersion,
    "org.tpolecat" %% "doobie-specs2-cats"     % doobieVersion,
    "org.typelevel" %% "cats" % "0.9.0",
    "com.chuusai" %% "shapeless" % "2.3.2",
    "com.github.pocketberserker" % "scodec-msgpack_2.11" % "0.6.0"

  ))
}
