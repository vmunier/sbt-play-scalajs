# sbt-play-scalajs

[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Download](https://api.bintray.com/packages/vmunier/scalajs/sbt-play-scalajs/images/download.svg) ](https://bintray.com/vmunier/scalajs/sbt-play-scalajs/_latestVersion)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/vmunier/sbt-play-scalajs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

sbt-play-scalajs is a SBT plugin which allows you to use Scala.js along with Play Framework.
It uses the [sbt-web](https://github.com/sbt/sbt-web) and [scala-js](https://github.com/scala-js/scala-js) plugins.

## Setup

Specify the sbt version in `project/build.properties` which needs to be 0.13.5 or higher:
```
sbt.version=0.13.5
```

Add the sbt plugin to the `project/plugins.sbt` file along with Play! and Scala.js:
```
addSbtPlugin("com.vmunier" % "sbt-play-scalajs" % "0.2.3")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.0")
```

Lastly, write the following configuration in `build.sbt`:
```
import sbt.Project.projectToRef

lazy val jsProjects = Seq(js)

lazy val jvm = project.settings(
  scalaJSProjects := jsProjects,
  pipelineStages := Seq(scalaJSProd)).
  enablePlugins(PlayScala).
  aggregate(jsProjects.map(projectToRef): _*)

lazy val js = project.enablePlugins(ScalaJSPlugin, ScalaJSPlay)
```

There are two auto-plugins in this plugin: ScalaJSPlay and PlayScalaJS. The workflow is the following:
* ScalaJSPlay plugin is enabled in ScalaJS projects
* PlayScalaJS is added to Play project. It is build on top of SbtWeb plugin and can also add itself automaticly to all projects that have SbtWeb enabled.
* ScalaJS projects are collected in scalaJSProjects setting key of PlayProject
* When compilation or testing takes place then PlayScalaJS plugins runs all required tasks on scalaJSProjects projects, copies the output to play assets and takes care about source maps.
So, both

To see the plugin in action, you can clone and run this [simple example application](https://github.com/vmunier/play-with-scalajs-example).

## Features

- Use the `scalaJSProjects` setting key to attach several Scala.js projects to the Play! project
- The `scalaJSProd` pipeline task generates the optimised javascript when running `start`, `stage` and `dist`
- Source Maps is _disabled in production_ by default to prevent your users from seeing the source files. But it can easily be enabled in production too by setting `(emitSourceMaps in fullOptJS) := true` in the Scala.js projects.
- Use the `sourceMapsDirectories` setting on a Scala.js project to specify additional directories containing Scala files needed for Source Maps. You would typically use this setting when your Scala.js project depends on another Scala.js project.
