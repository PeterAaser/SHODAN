import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val udashVersion = "0.4.0"
  val udashJQueryVersion = "1.0.0"
  val logbackVersion = "1.1.3"
  val jettyVersion = "9.3.11.v20160721"
  val doobieVersion = "0.4.1"

  val crossDeps = Def.setting(Seq[ModuleID](
    "io.udash" %%% "udash-core-shared" % udashVersion,
    "io.udash" %%% "udash-rpc-shared" % udashVersion,
    "com.typesafe" % "config" % "1.3.1"
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
    "com.github.nscala-time" %% "nscala-time" % "2.16.0",

    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-server" % jettyVersion,

    "io.udash" %% "udash-rpc-backend" % udashVersion,

    "co.fs2" %% "fs2-core" % "0.9.5",
    "co.fs2" %% "fs2-io" % "0.9.5",

    "org.scalactic" %% "scalactic" % "3.0.0",
    "org.scalactic" %% "scalactic" % "3.0.0" % "test",

    "org.tpolecat" %% "doobie-core-cats"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres-cats"   % doobieVersion,
    "org.tpolecat" %% "doobie-specs2-cats"     % doobieVersion,


    "io.spray" %%  "spray-json" % "1.3.3",
    "com.github.fommil" %% "spray-json-shapeless" % "1.3.0"

    // "com.chuusai" %% "shapeless" % "2.3.2"
    // "org.typelevel" %% "cats" % "0.9.0",
  ))
}
