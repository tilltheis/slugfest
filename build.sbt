name := "slugfest"

version := "1.0"

scalaVersion := "2.12.2"

enablePlugins(ScalaJSPlugin)

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.2",
  "org.akka-js" %%% "akkajsactor" % "1.2.5.2",
  "org.scalatest" %%% "scalatest" % "3.0.1" % "test",
  "org.akka-js" %%% "akkajstestkit" % "1.2.5.2" % "test")
