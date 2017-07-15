name := "scala-raft"

version := "1.0"

scalaVersion := "2.12.1"

lazy val root = (project in file(".")).
  settings(
    name := "scala-raft",
    version := s"$version",
    scalaVersion := s"$scalaVersion",
    mainClass in Compile := Some("Main")
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.9",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.9" % Test
)
