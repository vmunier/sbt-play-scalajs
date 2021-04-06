sys.props.get("plugin.version") match {
  case Some(version) => addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % version)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.5.1")
addSbtPlugin("org.scala-js"       % "sbt-jsdependencies"       % "1.0.2")
addSbtPlugin("com.typesafe.sbt"   % "sbt-twirl"                % "1.5.1")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.7.6")
