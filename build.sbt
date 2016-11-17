lazy val commonSettings = Seq(version := "0.1.0" , scalaVersion := "2.11.8")

lazy val doobieVersion = "0.3.1-SNAPSHOT"

scalaVersion := "2.11.8"

// dunno lol
scalaVersion in ThisBuild := "2.11.8"

scalacOptions ++= Seq("-feature", "language:-higherKinds")

lazy val clients = Seq(visualizer)

lazy val closedLoop = (project in file("closed-loop")).
  settings(commonSettings: _*).
  settings(
    name := "SHODAN",
    scalaJSProjects := clients,
    maxErrors := 20,
    pollInterval := 1000,
    libraryDependencies ++= Seq
      ( "com.typesafe.akka" %% "akka-actor" % "2.4.11"
      , "com.typesafe.akka" % "akka-stream_2.11" % "2.4.11"
      , "co.fs2" %% "fs2-core" % "0.9.0"
      , "co.fs2" %% "fs2-io" % "0.9.0"
      // , "com.github.mpilquist" %% "simulacrum" % "0.10.0"
      , "org.scalactic" %% "scalactic" % "3.0.0"
      , "org.scalactic" %% "scalactic" % "3.0.0" % "test"
      , "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test"
      , "org.tpolecat" % "doobie-core-cats_2.11" % "0.3.1-M1"
      , "org.tpolecat" % "doobie-postgres-cats_2.11" % "0.3.1-M1"
      , "org.scodec" %% "scodec-bits" % "1.1.2"
      , "org.scodec" %% "scodec-protocols" % "1.0.2"
      , "org.scodec" %% "scodec-stream" % "1.0.1"
      , "com.chuusai" %% "shapeless" % "2.3.2"
    )
  )

lazy val visualizer = (project in file("visualizer"))
  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
  .dependsOn(sharedJs)
  .settings(
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.1",
      "io.monix" %%% "monix" % "2.0.0"
    )
)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(scalaVersion := "2.11.8")
  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

resolvers += "Sonatype (releases)" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += Opts.resolver.sonatypeSnapshots

// addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// addCompilerPlugin("fail.sauce" %% "commas" % "0.1.1-SNAPSHOT")
