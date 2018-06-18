sys.props.get("plugin.version") match {
  case Some(version) => addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % version)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

val scalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).getOrElse("0.6.23")

// fast development turnaround when using sbt ~re-start
addSbtPlugin("io.spray"           % "sbt-revolver"             % "0.9.1")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.3.5")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % scalaJSVersion)
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.5.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-twirl"                % "1.3.15")
