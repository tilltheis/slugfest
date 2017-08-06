name := "scalajs-fun"

version := "1.0"

scalaVersion := "2.12.2"

enablePlugins(ScalaJSPlugin)

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.2",
  "eu.unicredit" %%% "akkajsactor" % "0.2.4.14")
