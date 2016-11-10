lazy val commonSettings = Seq(version := "0.1.0" , scalaVersion := "2.11.8")

lazy val doobieVersion = "0.3.1-SNAPSHOT"

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SHODAN",

    maxErrors := 20,
    pollInterval := 1000,
    libraryDependencies ++= Seq
      ( "com.typesafe.akka" %% "akka-actor" % "2.4.11"
      , "com.typesafe.akka" % "akka-stream_2.11" % "2.4.11"
      , "co.fs2" %% "fs2-core" % "0.9.0"
      , "co.fs2" %% "fs2-io" % "0.9.0"
      , "com.github.mpilquist" %% "simulacrum" % "0.10.0"
      , "org.scalactic" %% "scalactic" % "3.0.0"
      , "org.scalactic" %% "scalactic" % "3.0.0" % "test"
      , "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test"
      , "org.tpolecat" % "doobie-core-cats_2.11" % "0.3.1-M1"
      , "org.tpolecat" % "doobie-postgres-cats_2.11" % "0.3.1-M1"
      , "org.scodec" %% "scodec-bits" % "1.1.2"
      , "org.scodec" %% "scodec-protocols" % "1.0.2"
      , "org.scodec" %% "scodec-stream" % "1.0.1"
    )
  )

resolvers += "Sonatype (releases)" at "https://oss.sonatype.org/content/repositories/releases/"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
