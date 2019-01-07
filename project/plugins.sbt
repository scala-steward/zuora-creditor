addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "1.1.9")
addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

resolvers += Resolver.typesafeRepo("releases")
resolvers += Resolver.sonatypeRepo("public")