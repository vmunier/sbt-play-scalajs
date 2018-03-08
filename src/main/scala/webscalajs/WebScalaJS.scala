package webscalajs

import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.pipeline.Pipeline
import com.typesafe.sbt.web.{PathMapping, SbtWeb}
import org.scalajs.jsdependencies.sbtplugin.JSDependenciesPlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Def.Initialize
import sbt.Keys._
import sbt.Project.projectToRef
import sbt._

/**
  * Auto-plugin added to SbtWeb projects
  */
object WebScalaJS extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = allRequirements

  object autoImport {
    val scalaJSProjects = Def.settingKey[Seq[Project]]("Scala.js projects attached to the sbt-web project")

    val scalaJSDev = Def.taskKey[Pipeline.Stage]("Apply fastOptJS on all Scala.js projects")
    val scalaJSProd = Def.taskKey[Pipeline.Stage]("Apply fullOptJS on all Scala.js projects")

    val scalaJSPipeline = Def.taskKey[Pipeline.Stage]("Call scalaJSDev/scalaJSProd when dev/prod commands are called")
    val devCommands = Def.settingKey[Seq[String]]("Name of the commands used during development")
    val isDevMode = Def.taskKey[Boolean]("Whether the app runs in development mode (true) or production mode (false)")

    val monitoredScalaJSDirectories = Def.settingKey[Seq[File]]("Monitored Scala.js directories")
    val scalaJSDirectoriesFilter = Def.settingKey[FileFilter]("Filter that accepts all the monitored Scala.js directories")
    val scalaJSWatchSources = Def.taskKey[Seq[CrossSbtUtils.Source]]("Watch sources on all Scala.js projects")
  }
  import webscalajs.WebScalaJS.autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    scalaJSProjects := Seq(),

    scalaJSDev := scalaJSDevTask.value,
    scalaJSProd := scalaJSProdTask.value,

    isDevMode in scalaJSPipeline := isDevModeTask.value,
    devCommands in scalaJSPipeline := Seq("run", "compile", "re-start", "reStart", "runAll"),
    scalaJSPipeline := scalaJSPipelineTask.value,

    /**
      * The Scala.js directories are added to unmanagedSourceDirectories to be part of the directories monitored by Play run.
      *
      * @see playMonitoredFilesTask in Play, which creates the list of monitored directories https://github.com/playframework/playframework/blob/f5535aa08d639bae0f1734ebe3bc9aad7ce0f487/framework/src/sbt-plugin/src/main/scala/play/sbt/PlayCommands.scala#L85
      */
    unmanagedSourceDirectories in Assets ++= monitoredScalaJSDirectories.value,
    monitoredScalaJSDirectories := monitoredScalaJSDirectoriesSetting.value,

    /**
      * excludeFilter is updated to prevent SbtWeb from adding any descendant files from the Scala.js directories.
      * Dev and Prod tasks of this plugin select the Scala files that should be added.
      *
      * @see where excludeFilter is used in SbtWeb https://github.com/sbt/sbt-web/blob/cb7585f44fc1a00edca085a361f88cc1bf5ddd13/src/main/scala/com/typesafe/sbt/web/SbtWeb.scala#L245
      */
    excludeFilter in Assets := (excludeFilter in Assets).value || scalaJSDirectoriesFilter.value,
    scalaJSDirectoriesFilter := monitoredScalaJSDirectories.value.map(scalaJSDir => new SimpleFileFilter(f => scalaJSDir.getCanonicalPath == f.getCanonicalPath)).foldLeft(NothingFilter: FileFilter)(_ || _),
    scalaJSWatchSources := Def.taskDyn {
      taskOnProjects(transitiveDependencies(scalaJSProjects.value), watchSources)
    }.value,
    watchSources ++= scalaJSWatchSources.value,
    includeFilter in scalaJSDev := GlobFilter("*"),
    includeFilter in scalaJSProd := GlobFilter("*")
  )

  implicit class ProjectsImplicits(projects: Seq[Project]) {
    def toRefs: Seq[ProjectReference] = projects.map(projectToRef)
  }

  lazy val scalaJSPipelineTask: Initialize[Task[Pipeline.Stage]] = Def.taskDyn {
    if ((isDevMode in scalaJSPipeline).value) {
      scalaJSDev
    } else {
      scalaJSProd
    }
  }

  lazy val scalaJSDevTask: Initialize[Task[Pipeline.Stage]] = Def.task {
    val include = (includeFilter in scalaJSDev).value
    val exclude = (excludeFilter in scalaJSDev).value
    val compileDevFiles = devFiles(Compile).value
    val fastOptSourcemapScalaFiles = sourcemapScalaFiles(fastOptJS).value

    mappings: Seq[PathMapping] => {
      val filtered = filterMappings(mappings, include, exclude)
      filtered ++ compileDevFiles ++ fastOptSourcemapScalaFiles
    }
  }

  lazy val scalaJSProdTask: Initialize[Task[Pipeline.Stage]] = Def.task {
    val include = (includeFilter in scalaJSProd).value
    val exclude = (excludeFilter in scalaJSProd).value
    val compileProdFiles = prodFiles(Compile).value
    val fullOptSourcemapScalaFiles = sourcemapScalaFiles(fullOptJS).value

    mappings: Seq[PathMapping] => {
      val filtered = filterMappings(mappings, include, exclude)
      filtered ++ compileProdFiles ++ fullOptSourcemapScalaFiles
    }
  }

  lazy val isDevModeTask: Initialize[Task[Boolean]] = Def.task {
    (devCommands in scalaJSPipeline).value.contains(CrossSbtUtils.executedCommandKey.value)
  }

  private def filterMappings(mappings: Seq[PathMapping], include: FileFilter, exclude: FileFilter) = {
    for ((file, path) <- mappings if include.accept(file) && !exclude.accept(file))
      yield file -> path
  }

  lazy val monitoredScalaJSDirectoriesSetting: Initialize[Seq[File]] = Def.settingDyn {
    settingOnProjects(transitiveDependencies(scalaJSProjects.value), unmanagedSourceDirectories)
  }

  def devFiles(scope: Configuration): Initialize[Task[Seq[PathMapping]]] = {
    scalaJSOutput(scope)(Seq(packageJSDependencies), Seq(fastOptJS))
  }

  def prodFiles(scope: Configuration): Initialize[Task[Seq[PathMapping]]] = {
    scalaJSOutput(scope)(Seq(packageJSDependencies, packageMinifiedJSDependencies), Seq(fullOptJS))
  }

  def scalaJSOutput(scope: Configuration)(fileTKs: Seq[TaskKey[File]], attributedTKs: Seq[TaskKey[Attributed[File]]]): Initialize[Task[Seq[PathMapping]]] = Def.taskDyn {
    val filter = ScopeFilter(inProjects(scalaJSProjects.value.toRefs: _*), inConfigurations(scope))

    Def.task {
      val jsFiles = fileTKs.join.all(filter).value.flatten ++ attributedTKs.join.all(filter).value.flatten.map(_.data)
      jsFiles.flatMap { f =>
        // Non existing or empty files are ignored. The .map files do not necessarily exist (emitSourceMaps := false).
        Seq(f, new File(f.getCanonicalPath + ".map")).filter(_.length() != 0).map(f => f -> f.getName)
      }
    }
  }

  def transitiveDependencies[A](projects: Seq[Project]): Initialize[Seq[ProjectRef]] = Def.setting {
    projects.map(project =>
      thisProjectRef.all(ScopeFilter(inDependencies(project)))
    ).join.value.flatten
  }

  def sourcemapScalaFiles(optJS: TaskKey[Attributed[File]]): Initialize[Task[Seq[PathMapping]]] = Def.taskDyn {
    val projectsWithSourceMaps = filterInitializeSeq(
      scalaJSProjects,
      (p: Project) => Def.setting(scalaJSLinkerConfig.in(p, optJS).value.sourceMap)).value

    Def.task {
      val sourceDirectories = settingOnProjects(transitiveDependencies(projectsWithSourceMaps), unmanagedSourceDirectories).value
      for {
        (sourceDir, hashedPath) <- SourceMappings.fromFiles(sourceDirectories)
        scalaFiles = (sourceDir ** "*.scala").get
        (scalaFile, subPath) <- scalaFiles pair CrossSbtUtils.relativeTo(sourceDir)
      } yield (new File(scalaFile.getCanonicalPath), s"$hashedPath/$subPath")
    }
  }

  def filterInitializeSeq[A](settingKey: Initialize[Seq[A]], filter: A => Initialize[Boolean]): Initialize[Seq[A]] = Def.settingDyn {
    settingKey.value.foldLeft(Def.setting(Seq.empty[A])) { (tasksAcc, elt) =>
      Def.setting {
        val filtered = if (filter(elt).value) Seq(elt) else Seq.empty[A]
        filtered ++ tasksAcc.value
      }
    }
  }

  def taskOnProjects[A](projects: Initialize[Seq[ProjectRef]], action: Initialize[Task[Seq[A]]]): Initialize[Task[Seq[A]]] = Def.taskDyn {
    val scopeFilter = ScopeFilter(inProjects(projects.value: _*), inConfigurations(Compile))
    Def.task {
      action.all(scopeFilter).value.flatten
    }
  }

  def settingOnProjects[A](projects: Initialize[Seq[ProjectRef]], action: Initialize[Seq[A]]): Initialize[Seq[A]] = Def.settingDyn {
    val scopeFilter = ScopeFilter(inProjects(projects.value: _*), inConfigurations(Compile))
    Def.setting {
      action.all(scopeFilter).value.flatten
    }
  }
}
