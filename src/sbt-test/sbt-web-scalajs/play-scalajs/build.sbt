import sbtcrossproject.{crossProject, CrossType}

lazy val root = (project in file("."))
  .aggregate(server, client, sharedJs, sharedJvm)

lazy val server = (project in file("server"))
  .settings(commonSettings)
  .settings(
    scalaJSProjects := Seq(client),
    Assets / pipelineStages := Seq(scalaJSPipeline),
    // triggers scalaJSPipeline when using compile or continuous compilation
    Compile / compile := (Compile / compile).dependsOn(scalaJSPipeline).value,
    libraryDependencies ++= Seq(
      guice,
      specs2 % Test
    ),
    // Specific settings for sbt-test
    PlayKeys.playInteractionMode := play.sbt.StaticPlayNonBlockingInteractionMode,
    TaskKey[Unit]("resetReloads") := {
      val f = target.value / "reload.log"
      f.delete()
      f.createNewFile()
    },
    InputKey[Unit]("verifyReloads") := {
      val expected = Def.spaceDelimited().parsed.head.toInt
      val actual = IO.readLines(target.value / "reload.log").count(_.nonEmpty)
      if (expected == actual) {
        println(s"Expected and got $expected reloads")
      } else {
        sys.error(s"Expected $expected reloads but got $actual")
      }
    }
  )
  .enablePlugins(PlayScala)
  .dependsOn(sharedJvm)

lazy val client = (project in file("client"))
  .settings(commonSettings)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.1.0"
    )
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(sharedJs)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(commonSettings)
  .jsConfigure(_.enablePlugins(ScalaJSWeb))
lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

lazy val commonSettings = Seq(
  scalaVersion := "2.13.5",
  organization := "com.example",
  version := "0.1.0-SNAPSHOT"
)
