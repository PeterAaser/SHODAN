import com.lihaoyi.workbench._
import UdashBuild._
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

def crossLibs(configuration: Configuration) =
  libraryDependencies ++= crossDeps.value.map(_ % configuration)

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

lazy val backend = project.in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    libraryDependencies ++= backendDeps.value,
    crossLibs(Compile),

    compile <<= (compile in Compile),
    (compile in Compile) <<= (compile in Compile).dependsOn(copyStatics),
    copyStatics := IO.copyDirectory((crossTarget in frontend).value / StaticFilesDir, (target in Compile).value / StaticFilesDir),
    copyStatics <<= copyStatics.dependsOn(compileStatics in frontend),

    mappings in (Compile, packageBin) ++= {
      copyStatics.value
      ((target in Compile).value / StaticFilesDir).***.get map { file => file -> file.getAbsolutePath.stripPrefix((target in Compile).value.getAbsolutePath)
      }
    },

    watchSources ++= (sourceDirectory in frontend).value.***.get
  )

lazy val frontend = project.in(file("frontend")).enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    libraryDependencies ++= frontendDeps.value,
    crossLibs(Compile),
    jsDependencies ++= frontendJSDeps.value,
    persistLauncher in Compile := true,

    compile <<= (compile in Compile).dependsOn(compileStatics),
    compileStatics := {
      IO.copyDirectory(sourceDirectory.value / "main/assets/fonts", crossTarget.value / StaticFilesDir / WebContent / "assets/fonts")
      IO.copyDirectory(sourceDirectory.value / "main/assets/images", crossTarget.value / StaticFilesDir / WebContent / "assets/images")
      val statics = compileStaticsForRelease.value
      (crossTarget.value / StaticFilesDir).***.get
    },

    artifactPath in(Compile, fastOptJS) :=
      (crossTarget in(Compile, fastOptJS)).value / StaticFilesDir / WebContent / "scripts" / "frontend-impl-fast.js",
    artifactPath in(Compile, fullOptJS) :=
      (crossTarget in(Compile, fullOptJS)).value / StaticFilesDir / WebContent / "scripts" / "frontend-impl.js",
    artifactPath in(Compile, packageJSDependencies) :=
      (crossTarget in(Compile, packageJSDependencies)).value / StaticFilesDir / WebContent / "scripts" / "frontend-deps-fast.js",
    artifactPath in(Compile, packageMinifiedJSDependencies) :=
      (crossTarget in(Compile, packageMinifiedJSDependencies)).value / StaticFilesDir / WebContent / "scripts" / "frontend-deps.js",
    artifactPath in(Compile, packageScalaJSLauncher) :=
      (crossTarget in(Compile, packageScalaJSLauncher)).value / StaticFilesDir / WebContent / "scripts" / "frontend-init.js"
  ).settings(workbenchSettings:_*)
  .settings(
    bootSnippet := "com.cyborg.Init().main();",
    updatedJS := {
      var files: List[String] = Nil
      ((crossTarget in Compile).value / StaticFilesDir ** "*.js").get.foreach {
        (x: File) =>
          streams.value.log.info("workbench: Checking " + x.getName)
          FileFunction.cached(streams.value.cacheDirectory / x.getName, FilesInfo.lastModified, FilesInfo.lastModified) {
            (f: Set[File]) =>
              val fsPath = f.head.getAbsolutePath.drop(new File("").getAbsolutePath.length)
              files = "http://localhost:12345" + fsPath :: files
              f
          }(Set(x))
      }
      files
    },
    //// use either refreshBrowsers OR updateBrowsers
    // refreshBrowsers <<= refreshBrowsers triggeredBy (compileStatics in Compile)
    updateBrowsers <<= updateBrowsers triggeredBy (compileStatics in Compile)
  )

  
