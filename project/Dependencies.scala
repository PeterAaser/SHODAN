import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val doobieVersion = "0.5.0-RC2"
  val fs2Version = "0.10.2"
  val scodecVersion = "TODO"
  val http4sVersion = "0.18.0"
  val circeVersion = "0.9.1"
  val catsVersion = "1.0.1"
  val catsEffectVersion = "0.5"


  val crossDeps = Def.setting(Seq[ModuleID](
    "com.typesafe" % "config" % "1.3.1",                           // Dont think this is in use any longer

    "co.fs2" %%% "fs2-core" % fs2Version,                          // The best library ever

    "com.lihaoyi" % "ammonite" % "1.0.3" cross CrossVersion.full,  // never got this to work since I'm a brain damaged child

    "org.scodec" %%% "scodec-bits" % "1.1.2",                      // xvid codec required to play this file
    "org.scodec" %%% "scodec-core" % "1.10.3",
    "org.typelevel" %%% "cats-core" % catsVersion,                  // abstract category dork stuff

    "org.typelevel" %% "spire" % "0.14.1",                         // math dork stuff

    "com.lihaoyi" %%% "sourcecode" % "0.1.4"                       // expert println debugging

  ))


  val frontendDeps = Def.setting(Seq[ModuleID](
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",

    "com.github.japgolly.scalajs-react" %%% "core" % "1.1.0",
    "com.github.japgolly.scalajs-react" %%% "extra" % "1.1.0",
    "com.olvind" %%% "scalajs-react-components" % "0.8.0"
  ))


  val frontendJSDeps = Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID](
    "org.webjars.bower" % "react" % "15.6.1"
      /        "react-with-addons.js"
      minified "react-with-addons.min.js"
      commonJSName "React",

    "org.webjars.bower" % "react" % "15.6.1"
      /         "react-dom.js"
      minified  "react-dom.min.js"
      dependsOn "react-with-addons.js"
      commonJSName "ReactDOM",

    "org.webjars.bower" % "react" % "15.6.1"
      /         "react-dom-server.js"
      minified  "react-dom-server.min.js"
      dependsOn "react-dom.js"
      commonJSName "ReactDOMServer"
  ))


  val backendDeps = Def.setting(Seq[ModuleID](

    "io.circe" %% "circe-core" % circeVersion,                // JSON
    "io.circe" %% "circe-generic" % circeVersion,             // JSON
    "io.circe" %% "circe-parser" % circeVersion,              // JSON

    // Optional for auto-derivation of JSON codecs
    "io.circe" %% "circe-generic" % circeVersion,             // JSON

    // Optional for string interpolation to JSON model
    "io.circe" %% "circe-literal" % circeVersion,             // JSON

    "org.http4s" %% "http4s-circe" % http4sVersion,           // JSON

    "com.chuusai" %% "shapeless" % "2.3.2",                   // Abstract level category dork stuff

    "org.http4s" %% "http4s-dsl" % http4sVersion,             // HTTP server and client
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,    // HTTP server and client
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,    // HTTP server and client
    "org.http4s" %% "http4s-server" % http4sVersion,          // HTTP server and client

    "com.github.nscala-time" %% "nscala-time" % "2.16.0",     // Time

    "org.typelevel" %% "cats-effect" % catsEffectVersion,     // IO monad category wank

    "co.fs2" %% "fs2-core" % fs2Version,                      // The best library
    "co.fs2" %% "fs2-io"   % fs2Version,                      // The best library

    "org.tpolecat" %% "doobie-core"       % doobieVersion,    // Databases. Unironically uses comonads
    "org.tpolecat" %% "doobie-postgres"   % doobieVersion,    // Databases. Unironically uses comonads
    "org.tpolecat" %% "doobie-specs2"     % doobieVersion,    // Databases. Unironically uses comonads
    "net.postgis" % "postgis-jdbc" % "2.2.1",                 // Pull this and a lovecraftian error message goes away.
  ))
}
