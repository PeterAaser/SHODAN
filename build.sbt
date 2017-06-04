import com.lihaoyi.workbench._
// import UdashBuild._
import Dependencies._

name := "SHODAN"

version in ThisBuild := "0.1.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.11.11"
organization in ThisBuild := "com.cyborg"
crossPaths in ThisBuild := false
scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:existentials",
  "-language:dynamics",
  "-language:higherKinds",
  "-Xfuture",
  "-Xfatal-warnings",
  "-Xlint:_,-missing-interpolator,-adapted-args"
)


fork in run := true

/**
  collect shared dependencies from Dependencies.scala
  */
def crossLibs(configuration: Configuration) =
  libraryDependencies ++= crossDeps.value.map(_ % configuration)


/**
  Root of the project, basically aggregates shared, front and back
  */
lazy val SHODAN = project.in(file("."))
  .aggregate(sharedJS, sharedJVM, frontend, backend)
  .dependsOn(backend)
  .settings(
    publishArtifact := false,
    mainClass in Compile := Some("com.cyborg.Launcher")
  )


lazy val shared = crossProject.crossType(CrossType.Pure).in(file("shared"))
  .settings(
    crossLibs(Provided)
  )


lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js


/**
  Honestly I have no fucking clue what any of this stuff does
  */
lazy val backend = project.in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    libraryDependencies ++= backendDeps.value,
    crossLibs(Compile),

    watchSources ++= (sourceDirectory in frontend).value.***.get
  )


/**
  Same as above. I dunno man
  */
lazy val frontend = project.in(file("frontend")).enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    libraryDependencies ++= frontendDeps.value,
    crossLibs(Compile),
    jsDependencies ++= frontendJSDeps.value,
    persistLauncher in Compile := true

  ).settings(workbenchSettings:_*)
  .settings(
    bootSnippet := "com.cyborg.Init().main();"
  )
