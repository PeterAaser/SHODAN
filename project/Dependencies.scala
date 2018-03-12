import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {
  val versionOfScala = "2.12.4"

  // Udash
  val udashVersion = "0.6.0"
  val udashJQueryVersion = "1.1.0"

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

  // Non-udash
  val doobieVersion = "0.5.1"
  val fs2Version = "0.10.2"
  val http4sVersion = "0.18.0"
  val circeVersion = "0.9.1"
  val catsVersion = "1.0.1"
  val catsEffectVersion = "0.9"

  // Dependencies for both frontend and backend
  // Those have to be cross-compilable
  val crossDeps = Def.setting(Seq(
    "io.udash" %%% "udash-core-shared" % udashVersion,
    "io.udash" %%% "udash-rpc-shared" % udashVersion,
    "io.udash" %%% "udash-rest-shared" % udashVersion,
    "io.udash" %%% "udash-i18n-shared" % udashVersion,
    "io.udash" %%% "udash-css-shared" % udashVersion,
    "io.udash" %%% "udash-auth-shared" % udashVersion,

    "co.fs2" %%% "fs2-core" % fs2Version,                          // The best library ever
    "org.typelevel" %%% "cats-core" % catsVersion,                 // abstract category dork stuff
    "org.typelevel" %% "spire" % "0.14.1",                         // math dork stuff
    "com.lihaoyi" %%% "sourcecode" % "0.1.4",                      // expert println debugging
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
  ))

  // JavaScript libraries dependencies
  // Those will be added into frontend-deps.js
  val frontendJSDeps = Def.setting(Seq(
    // "jquery.js" is provided by "udash-jquery" dependency
    "org.webjars" % "bootstrap" % bootstrapVersion /
      "bootstrap.js" minified "bootstrap.min.js" dependsOn "jquery.js",

    // Highcharts JS files
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/highcharts.src.js" minified s"$highchartsVersion/highcharts.js" dependsOn "jquery.js",
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/highcharts-3d.src.js" minified s"$highchartsVersion/highcharts-3d.js" dependsOn s"$highchartsVersion/highcharts.src.js",
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/highcharts-more.src.js" minified s"$highchartsVersion/highcharts-more.js" dependsOn s"$highchartsVersion/highcharts.src.js",
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/modules/exporting.src.js" minified s"$highchartsVersion/modules/exporting.js" dependsOn s"$highchartsVersion/highcharts.src.js",
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/modules/drilldown.src.js" minified s"$highchartsVersion/modules/drilldown.js" dependsOn s"$highchartsVersion/highcharts.src.js",
    "org.webjars" % "highcharts" % highchartsVersion /
      s"$highchartsVersion/modules/heatmap.src.js" minified s"$highchartsVersion/modules/heatmap.js" dependsOn s"$highchartsVersion/highcharts.src.js",


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

    // server logging backend

    "io.circe" %% "circe-core" % circeVersion,                // JSON
    "io.circe" %% "circe-generic" % circeVersion,             // JSON
    // "io.circe" %% "circe-parser" % circeVersion,              // JSON


    // // Optional for string interpolation to JSON model
    // "io.circe" %% "circe-literal" % circeVersion,             // JSON
    // "org.http4s" %% "http4s-circe" % http4sVersion,           // JSON

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
    // "net.postgis" % "postgis-jdbc" % "2.2.1",                 // Pull this and a lovecraftian error message goes away.
  ))

  // Test dependencies
  val crossTestDeps = Def.setting(Seq(
    "org.scalatest" %%% "scalatest" % scalatestVersion,
    "org.scalamock" %%% "scalamock-scalatest-support" % scalamockVersion
  ).map(_ % Test))
}
