
name         := "macrowave"

organization := "com.github.zen-pie"

version      := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin(
  "org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full
)

libraryDependencies ++= Seq(
  "com.chuusai"            %% "shapeless"                % "2.3.2",
  "org.scalatest"          %% "scalatest"                % "3.0.1" % "test",
  "org.scala-lang"         % "scala-reflect"             % scalaVersion.value,
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"
)
