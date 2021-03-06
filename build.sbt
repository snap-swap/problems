name := "problems"

organization := "com.snapswap"

version := "1.0.5"

crossScalaVersions := Seq("2.11.12", "2.12.5")

scalacOptions := Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
//  "-Xfatal-warnings",
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
  val akkaV = "2.4.19"
  val akkaHttpV = "10.0.10"
  Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
  )
}

fork in Test := true

javaOptions in Test += "-Xmx512m"

testOptions in Test += Tests.Argument("-u", "console")
