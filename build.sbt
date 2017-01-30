name := "zuora-crediter"
description:= "This project contains a set of services and Lambda functions which find negative invoices and converts " +
  "them into a credit balance on the user's account, so that the amount is discounted off their next positive bill"
version       := "0.0.1"
scalaVersion := "2.11.8"
organization := "com.gu.zuora"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-target:jvm-1.8",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  //"-Xlint",
  //"-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

lazy val root = (project in file(".")).enablePlugins(ScalaxbPlugin, RiffRaffArtifact, JavaAppPackaging)
val dispatchV = "0.11.3" // change this to appropriate dispatch version

scalaxbDispatchVersion in (Compile, scalaxb) := dispatchV
scalaxbPackageName in (Compile, scalaxb) := "com.gu.zuora.soap"
scalaxbAsync in (Compile, scalaxb) := false

topLevelDirectory in Universal := None
packageName in (Compile, scalaxb) := "com.gu.zuora.soap"

riffRaffPackageType := (packageZipTarball in config("universal")).value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := "MemSub::Subscriptions"

addCommandAlias("dist", ";riffRaffArtifact")

resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2" % "compile",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1" % "compile",
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "net.databinder.dispatch" %% "dispatch-core" % dispatchV,
  "io.spray" %%  "spray-json" % "1.3.3",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "com.github.melrief" %% "purecsv" % "0.0.9",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

initialize := {
  val _ = initialize.value
  assert(sys.props("java.specification.version") == "1.8", "Java 8 is required for this project.")
}
