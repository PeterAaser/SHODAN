// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.9")

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

resolvers += Opts.resolver.sonatypeSnapshots

addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.0")

autoCompilerPlugins := true

// libraryDependencies +=
//   compilerPlugin("fail.sauce" %% "commas" % "0.1.1-SNAPSHOT")
