import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Dependencies {
  val versionOfScala = "2.12.7"
  // val versionOfScala = "2.13.0-M5"

  val doobieVersion     = "0.6.0"
  val fs2Version        = "1.0.4"
  val http4sVersion     = "0.20.0-M3"  // 19 is EOL, 20 is milestone stage. YOLO
  val circeVersion      = "0.10.1"
  val catsVersion       = "1.5.0"
  val catsEffectVersion = "1.1.0"
  val spireVersion      = "0.16.0"
  val monocleVersion    = "1.5.0"



  // webshit ??
  val ScalaTagsVersion = "0.6.2"
  val ScalaRxVersion = "0.3.2"
  val jqueryVersion = "3.2.1"


  // Udash
  val udashVersion = "0.8.0-M3"
  val udashJQueryVersion = "1.2.0"

  // Backend
  val avsystemCommonsVersion = "1.25.6"
  val jettyVersion = "9.4.8.v20171121"
  val logbackVersion = "1.2.3"

  // JS dependencies
  val bootstrapVersion = "3.3.7-1"
  val highchartsVersion = "5.0.10"

  // Testing
  val scalatestVersion = "3.0.4"
  val scalamockVersion = "3.6.0"


  // Dependencies for both frontend and backend
  // Those have to be cross-compilable
  val crossDeps = Def.setting(Seq(
    "io.udash" %%% "udash-core-shared" % udashVersion,
    "io.udash" %%% "udash-rpc-shared" % udashVersion,
    "io.udash" %%% "udash-rest-shared" % udashVersion,
    "io.udash" %%% "udash-i18n-shared" % udashVersion,
    "io.udash" %%% "udash-css-shared" % udashVersion,
    "io.udash" %%% "udash-auth-shared" % udashVersion,

    "org.typelevel" %%% "cats-core" % catsVersion,                 // abstract category dork stuff
    "org.typelevel" %%% "spire" % spireVersion,
    "com.lihaoyi" %%% "sourcecode" % "0.1.4",                      // expert println debugging
    "com.lihaoyi" %%% "pprint" % "0.5.3",                          // pretty print for types and case classes

    "com.github.julien-truffaut" %%%  "monocle-core"  % monocleVersion,
    "com.github.julien-truffaut" %%%  "monocle-macro" % monocleVersion,
  ))


  // Dependencies compiled to JavaScript code
  val frontendDeps = Def.setting(Seq(
    "io.udash" %%% "udash-core-frontend" % udashVersion,
    "io.udash" %%% "udash-rpc-frontend" % udashVersion,
    "io.udash" %%% "udash-i18n-frontend" % udashVersion,
    "io.udash" %%% "udash-css-frontend" % udashVersion,
    "io.udash" %%% "udash-auth-frontend" % udashVersion,

    // type-safe wrapper for Twitter Bootstrap
    "io.udash" %%% "udash-bootstrap" % udashVersion,
    // type-safe wrapper for Highcharts
    "io.udash" %%% "udash-charts" % udashVersion,

    // type-safe wrapper for jQuery
    "io.udash" %%% "udash-jquery" % udashJQueryVersion,


    "com.github.karasiq" %%% "scalajs-bootstrap" % "2.3.1",
    "com.lihaoyi" %%% "scalatags" % ScalaTagsVersion,
    "com.lihaoyi" %%% "scalarx" % ScalaRxVersion,

    "com.zoepepper" %%% "scalajs-jsjoda" % "1.1.1",
    "com.zoepepper" %%% "scalajs-jsjoda-as-java-time" % "1.1.1"
  ))

  // JavaScript libraries dependencies
  // Those will be added into frontend-deps.js
  val frontendJSDeps = Def.setting(Seq(

    "org.webjars" % "bootstrap" % bootstrapVersion /
      "bootstrap.js" minified "bootstrap.min.js" dependsOn "jquery.js",

    "org.webjars.npm" % "js-joda" % "1.1.8" / "dist/js-joda.js" minified "dist/js-joda.min.js"


  ))

  // Dependencies for JVM part of code
  val backendDeps = Def.setting(Seq(
    "io.udash" %% "udash-rpc-backend" % udashVersion,
    "io.udash" %% "udash-rest-backend" % udashVersion,
    "io.udash" %% "udash-i18n-backend" % udashVersion,
    "io.udash" %% "udash-css-backend" % udashVersion,

    "ch.qos.logback" % "logback-classic" % logbackVersion,

    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-server" % jettyVersion,

    // JSON
    "io.circe" %% "circe-core"     % circeVersion,
    "io.circe" %% "circe-generic"  % circeVersion,
    "io.circe" %% "circe-parser"   % circeVersion,
    "io.circe" %% "circe-literal"  % circeVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,

    // Abstract level category dork stuff
    "com.chuusai" %% "shapeless" % "2.3.3",

    // HTTP server and client
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-server" % http4sVersion,
    "joda-time" % "joda-time" % "2.9.9",
    "org.joda" % "joda-convert" % "2.0.1",

    // IO and effects
    "org.typelevel" %% "cats-effect" % catsEffectVersion,

    // 10/10
    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io"   % fs2Version,

    // GraphStream for visualizing RBN reservoir
    "org.graphstream" % "gs-core" % "1.3",
    // JFreeChart for visualizing integer streams
    "org.jfree" % "jfreechart" % "1.5.0",
    "org.jfree" % "jcommon" % "1.0.24",

    // Databases. Unironically uses comonads
    "org.tpolecat" %% "doobie-core"       % doobieVersion,
    "org.tpolecat" %% "doobie-postgres"   % doobieVersion,    // Databases. Unironically uses comonads
    "org.tpolecat" %% "doobie-specs2"     % doobieVersion,    // Databases. Unironically uses comonads
  ))

  // lol testing

  // Test dependencies
  // val crossTestDeps = Def.setting(Seq(
  //   "org.scalatest" %%% "scalatest" % scalatestVersion,
  //   "org.scalamock" %%% "scalamock-scalatest-support" % scalamockVersion
  // ).map(_ % Test))
}
