.PHONY: sbtconf purge scaffold

sbtconf:
	mkdir -p ~/.sbt/0.13/plugins
	echo 'resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"' > ~/.sbt/0.13/global.sbt
	echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")' > ~/.sbt/0.13/plugins/build.sbt
	echo 'addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.15")' >> ~/.sbt/0.13/plugins/plugins.sbt
	echo 'addSbtPlugin("com.scalapenos" % "sbt-prompt" % "1.0.0")' >> ~/.sbt/0.13/plugins/plugins.sbt

purge:
	-rm -rf target/
	-rm -rf project/project/
	-rm -rf project/target/
	-rm -rf backend/target/
	-rm -rf frontend/target/
	-rm -rf shared/.js/target/
	-rm -rf shared/.jvm/target/
	-rm -rf ~/.ivy2
	-rm -rf ~/.coursier
	-rm -rf ~/.sbt/1.0/plugins/project/
	-rm -rf ~/.sbt/1.0/plugins/target/

scaffold:
	createuser memer --pwprompt
	createdb memestorage --owner=memer
	psql -U memer -d memestorage -a -f sql/memescheme.sql
	psql -U memer memestorage < sql/memestorage.dump
