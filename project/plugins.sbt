// The Play plugin
// addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.9")

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

resolvers += Opts.resolver.sonatypeSnapshots

// addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.0")

autoCompilerPlugins := true


////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////// MONIX template plugins

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.url("heroku-sbt-plugin-releases",
                          url("https://dl.bintray.com/heroku/sbt-plugins/"))(Resolver.ivyStylePatterns)

// Sbt plugins
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.6")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.11")

addSbtPlugin("com.vmunier" % "sbt-play-scalajs" % "0.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("com.heroku" % "sbt-heroku" % "0.5.3.1")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
