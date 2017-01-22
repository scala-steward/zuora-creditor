addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.9.7")
addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")

resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeRepo("public")