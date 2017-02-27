name := "theater"

version := "1.0"

scalaVersion := "2.11.8"



val akkaDeps = {
  val akkaVersion = "2.4.17"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test"
  )
}



val mailDeps = {
  val mailVersion = "1.5.6"
  Seq(
    "com.sun.mail" % "javax.mail" % mailVersion,
    "javax.mail" % "javax.mail-api" % mailVersion
  )
}

libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.9.2",
  "com.typesafe.akka" %% "akka-http" % "10.0.4",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
) ++ akkaDeps ++ mailDeps