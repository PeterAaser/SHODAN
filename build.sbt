// resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"

name := "shodan"

inThisBuild(Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := Dependencies.versionOfScala,
  organization := "cyborg",
  scalacOptions ++= Seq(

    // Emit warning and location for usages of features that should be imported explicitly.
    // Det trenger vi vel egentlig ikke, blir bare masse støy
    // "-feature",

    "-explaintypes",

    // Lol no HKT
    "-language:higherKinds",

    // Under tvil, kan fint taes av lokalt
    "-deprecation",

    // Enable additional warnings where generated code depends on assumptions.
    "-unchecked",

    // Allow definition of implicit functions called views
    "-language:implicitConversions",

    // Existential types (besides wildcard types) can be written and inferred
    "-language:existentials",

    // "-language:dynamics",

    // Turn on future language features
    "-Xfuture",

    // Allows 2 second as opposed to 2.second
    // Generally adviced against, but I kinda like having them
    "-language:postfixOps",

    // Makes inference work better for partially parametrized types
    // such as foo[F[_], A]
    "-Ypartial-unification",

    // Completery moronic discarding of values.
    // Had a really nasty bug where IO[IO[Unit]] was accepted when the signature was IO[Unit]
    // Nightmare to debug...
    "-Ywarn-value-discard"


    // Yeah no
    // "-Xfatal-warnings",
    // "-Xlint:_,-missing-interpolator,-adapted-args"
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.8" cross CrossVersion.binary),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
))

resolvers += Resolver.sonatypeRepo("releases")

val TestAndCompileDep = "test->test;compile->compile"

// Custom SBT tasks
val copyAssets = taskKey[Unit]("Copies all assets to the target directory.")
val cssDir = settingKey[File]("Target for `compileCss` task.")
val compileCss = taskKey[Unit]("Compiles CSS files.")
val compileStatics = taskKey[File](
  "Compiles JavaScript files and copies all assets to the target directory."
)
val compileAndOptimizeStatics = taskKey[File](
  "Compiles and optimizes JavaScript files and copies all assets to the target directory."
)


// Reusable settings for all modules
val commonSettings = Seq(
  moduleName := "shodan-" + moduleName.value,
)

// Reusable settings for modules compiled to JS
val commonJSSettings = Seq(
  emitSourceMaps in Compile := true,
)

lazy val root = project.in(file("."))
  .aggregate(sharedJS, sharedJVM, frontend, backend, packager)
  .dependsOn(backend)
  .settings(
    publishArtifact := false,
    Compile / mainClass := Some("cyborg.backend.Launcher")
  )

lazy val shared = crossProject
  .crossType(CrossType.Pure).in(file("shared"))
  .settings(commonSettings)
  .jsSettings(commonJSSettings)
  .settings(
    libraryDependencies ++= Dependencies.crossDeps.value,
    // libraryDependencies ++= Dependencies.crossTestDeps.value
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS = shared.js

lazy val backend = project.in(file("backend"))
  .dependsOn(sharedJVM % TestAndCompileDep)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.backendDeps.value,
    Compile / mainClass := Some("cyborg.backend.Launcher"),
    )

val frontendWebContent = "UdashStatics/WebContent"
lazy val frontend = project.in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  // .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(sharedJS % TestAndCompileDep)
  .settings(commonSettings)
  .settings(commonJSSettings)
  .settings(
    libraryDependencies ++= Dependencies.frontendDeps.value,
    jsDependencies ++= Dependencies.frontendJSDeps.value, // native JS dependencies

    // Make this module executable in JS
    Compile / mainClass := Some("cyborg.frontend.JSLauncher"),
    scalaJSUseMainModuleInitializer := true,

    // Implementation of custom tasks defined above
    copyAssets := {
      IO.copyDirectory(
        sourceDirectory.value / "main/assets",
        target.value / frontendWebContent / "assets"
      )
      IO.copyFile(
        sourceDirectory.value / "main/assets/index.html",
        target.value / frontendWebContent / "index.html"
      )
    },

    // Compiles CSS files and put them in the target directory
    cssDir := (Compile / fastOptJS / target).value / frontendWebContent / "styles",
    compileCss := Def.taskDyn {
      val dir = (Compile / cssDir).value
      val path = dir.absolutePath
      dir.mkdirs()
      (backend / Compile / runMain).toTask(s" cyborg.backend.css.CssRenderer $path false")
    }.value,

    // Compiles JS files without full optimizations
    compileStatics := { (Compile / fastOptJS / target).value / "UdashStatics" },
    compileStatics := compileStatics.dependsOn(
      Compile / fastOptJS, Compile / copyAssets, Compile / compileCss
    ).value,

    // Compiles JS files with full optimizations
    compileAndOptimizeStatics := { (Compile / fullOptJS / target).value / "UdashStatics" },
    compileAndOptimizeStatics := compileAndOptimizeStatics.dependsOn(
      Compile / fullOptJS, Compile / copyAssets, Compile / compileCss
    ).value,

    // Target files for Scala.js plugin
    Compile / fastOptJS / artifactPath :=
      (Compile / fastOptJS / target).value /
        frontendWebContent / "scripts" / "frontend.js",
    Compile / fullOptJS / artifactPath :=
      (Compile / fullOptJS / target).value /
        frontendWebContent / "scripts" / "frontend.js",
    Compile / packageJSDependencies / artifactPath :=
      (Compile / packageJSDependencies / target).value /
        frontendWebContent / "scripts" / "frontend-deps.js",
    Compile / packageMinifiedJSDependencies / artifactPath :=
      (Compile / packageMinifiedJSDependencies / target).value /
        frontendWebContent / "scripts" / "frontend-deps.js"
  )


lazy val packager = project
  .in(file("packager"))
  .dependsOn(backend)
  .enablePlugins(JavaServerAppPackaging)
  .settings(commonSettings)
  .settings(
    normalizedName := "shodan",
    Compile / mainClass := (backend / Compile / mainClass).value,

    // add frontend statics to the package
    Universal / mappings ++= {
      import Path.relativeTo
      val frontendStatics = (frontend / Compile / compileAndOptimizeStatics).value
      (frontendStatics.allPaths --- frontendStatics) pair relativeTo(frontendStatics.getParentFile)
    },
  )

// Ability to interrupt task execution with C-c
cancelable in Global := true
fork := true
