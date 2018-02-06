mkdir -p ~/.sbt/0.13/plugins
echo 'resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"' > ~/.sbt/0.13/global.sbt
echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC3")' > ~/.sbt/0.13/plugins/build.sbt
echo 'addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.15")' >> ~/.sbt/0.13/plugins/plugins.sbt
echo 'addSbtPlugin("com.scalapenos" % "sbt-prompt" % "1.0.0")' >> ~/.sbt/0.13/plugins/plugins.sbt

