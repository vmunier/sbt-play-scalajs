import sbtcrossproject.{crossProject, CrossType}
import org.scalajs.linker.interface.ModuleInitializer

lazy val root = (project in file("."))
  .aggregate(server, firstClient, secondClient, sharedJs, sharedJvm)

lazy val server = (project in file("server")).settings(commonSettings).settings(
  scalaJSProjects := Seq(firstClient, secondClient),
  Assets / pipelineStages := Seq(scalaJSPipeline),
  // triggers scalaJSPipeline when using compile or continuous compilation
  Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http" % "10.1.13",
    "com.typesafe.akka" %% "akka-stream" % "2.6.10"
  ),
  Assets / WebKeys.packagePrefix := "public/",
  Runtime / managedClasspath += (Assets / packageBin).value
).enablePlugins(SbtWeb, SbtTwirl, JavaAppPackaging).
  dependsOn(sharedJvm)

lazy val firstClient = (project in file("firstClient")).settings(commonSettings).settings(
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "1.1.0",
  jsDependencies += "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js",
  Compile / fastLinkJS / jsMappings += toPathMapping((Compile / packageJSDependencies).value),
  Compile / fullLinkJS / jsMappings += toPathMapping((Compile / packageMinifiedJSDependencies).value)
).enablePlugins(ScalaJSPlugin, ScalaJSWeb, JSDependenciesPlugin).
  dependsOn(sharedJs)

def toPathMapping(f: File): (File, String) = f -> f.getName

lazy val secondClient = (project in file("secondClient")).settings(commonSettings).settings(
  Compile / scalaJSModuleInitializers +=
    ModuleInitializer.mainMethod("com.example.akkahttpscalajs.AppB", "main").withModuleID("b"),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) }
).enablePlugins(ScalaJSPlugin, ScalaJSWeb)

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
