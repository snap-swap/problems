name := "problems"

organization := "com.snapswap"

version := "1.0.0"

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture",
  "-Ywarn-unused-import",
  "-encoding",
  "UTF-8"
)

resolvers ++= Seq(
  "SnapSwap repo" at "https://dev.snapswap.vc/artifactory/libs-release",
  "SnapSwap snapshot repo" at "https://dev.snapswap.vc/artifactory/libs-snapshot/"
)

libraryDependencies ++= {
  val akkaV = "2.4.11"
  Seq(
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV % "test"
  )
}

fork in Test := true

javaOptions in Test += "-Xmx512m"

testOptions in Test += Tests.Argument("-u", "console")
