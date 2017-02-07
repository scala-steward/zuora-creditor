addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.9.7")
addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeRepo("public")