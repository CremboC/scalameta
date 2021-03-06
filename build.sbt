import java.io._
import scala.util.Try
import scala.xml.{Node => XmlNode, NodeSeq => XmlNodeSeq, _}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import org.scalajs.sbtplugin.ScalaJSCrossVersion
import org.scalameta.os
import UnidocKeys._
import sbt.ScriptedPlugin._
import com.trueaccord.scalapb.compiler.Version.scalapbVersion

lazy val LanguageVersions = Seq("2.11.10", "2.12.1")
lazy val LanguageVersion = LanguageVersions.head
lazy val LibraryVersion = sys.props.getOrElseUpdate("scalameta.version", os.version.preRelease())

// ==========================================
// Projects
// ==========================================

name := "scalametaRoot"
sharedSettings
nonPublishableSettings
unidocSettings
// ci-fast is not a CiCommand because `plz x.y.z test` is super slow,
// it runs `test` sequentially in every defined module.
commands += Command.command("ci-fast") { s =>
  s"wow $ciScalaVersion" ::
    "tests/test" ::
    ci("doc") :: // skips 2.10 projects
    s
}
commands += CiCommand("ci-slow")(
  "scalahostNsc/test:runMain scala.meta.tests.scalahost.converters.LotsOfProjects" ::
    "testkit/test:runMain scala.meta.testkit.ScalametaParserPropertyTest" ::
    Nil
)
commands += CiCommand("ci-sbt-scalahost")("scalahostSbt/test" :: Nil)
commands += CiCommand("ci-publish")(
  if (sys.env.contains("CI_PUBLISH")) s"publish" :: Nil
  else Nil
)
// NOTE: These tasks are aliased here in order to support running "tests/test"
// from a command. Running "test" inside a command will trigger the `test` task
// to run in all defined modules, including ones like inputs/io/dialects which
// have no tests.
test := test.in(tests).value
testJVM := testJVM.in(tests).value
testJS := testJS.in(tests).value
packagedArtifacts := Map.empty
unidocProjectFilter.in(ScalaUnidoc, unidoc) := inAnyProject
console := console.in(scalametaJVM, Compile).value

lazy val common = crossProject
  .in(file("scalameta/common"))
  .settings(
    publishableSettings,
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.1.3",
    description := "Bag of private and public helpers used in scala.meta's APIs and implementations",
    enableMacros
  )
lazy val commonJVM = common.jvm
lazy val commonJS = common.js

lazy val io = crossProject
  .in(file("scalameta/io"))
  .settings(
    publishableSettings,
    description := "Scala.meta's API for JVM/JS agnostic IO."
  )

lazy val ioJVM = io.jvm
lazy val ioJS = io.js

lazy val dialects = crossProject
  .in(file("scalameta/dialects"))
  .settings(
    publishableSettings,
    description := "Scala.meta's dialects",
    enableMacros
  )
  .dependsOn(common)
lazy val dialectsJVM = dialects.jvm
lazy val dialectsJS = dialects.js

lazy val inline = crossProject
  .in(file("scalameta/inline"))
  .settings(
    publishableSettings,
    description := "Scala.meta's APIs for new-style (\"inline\") macros"
  )
  .dependsOn(inputs)
lazy val inlineJVM = inline.jvm
lazy val inlineJS = inline.js

lazy val inputs = crossProject
  .in(file("scalameta/inputs"))
  .settings(
    publishableSettings,
    description := "Scala.meta's APIs for source code in textual format",
    enableMacros
  )
  .dependsOn(common, io)
lazy val inputsJVM = inputs.jvm
lazy val inputsJS = inputs.js

lazy val parsers = crossProject
  .in(file("scalameta/parsers"))
  .settings(
    publishableSettings,
    description := "Scala.meta's API for parsing and its baseline implementation"
  )
  .dependsOn(common, dialects, inputs, tokens, tokenizers, trees)
lazy val parsersJVM = parsers.jvm
lazy val parsersJS = parsers.js

lazy val quasiquotes = crossProject
  .in(file("scalameta/quasiquotes"))
  .settings(
    publishableSettings,
    description := "Scala.meta's quasiquotes for abstract syntax trees",
    enableHardcoreMacros
  )
  .dependsOn(common, dialects, inputs, trees, parsers)
lazy val quasiquotesJVM = quasiquotes.jvm
lazy val quasiquotesJS = quasiquotes.js

lazy val tokenizers = crossProject
  .in(file("scalameta/tokenizers"))
  .settings(
    publishableSettings,
    description := "Scala.meta's APIs for tokenization and its baseline implementation",
    libraryDependencies += "com.lihaoyi" %%% "scalaparse" % "0.4.2",
    enableMacros
  )
  .dependsOn(common, dialects, inputs, tokens)
lazy val tokenizersJVM = tokenizers.jvm
lazy val tokenizersJS = tokenizers.js

lazy val tokens = crossProject
  .in(file("scalameta/tokens"))
  .settings(
    publishableSettings,
    description := "Scala.meta's tokens and token-based abstractions (inputs and positions)",
    enableMacros
  )
  .dependsOn(common, dialects, inputs)
lazy val tokensJVM = tokens.jvm
lazy val tokensJS = tokens.js

lazy val transversers = crossProject
  .in(file("scalameta/transversers"))
  .settings(
    publishableSettings,
    description := "Scala.meta's traversal and transformation infrastructure for abstract syntax trees",
    enableMacros
  )
  .dependsOn(common, trees)
lazy val traversersJVM = transversers.jvm
lazy val traversersJS = transversers.js

lazy val trees = crossProject
  .in(file("scalameta/trees"))
  .settings(
    publishableSettings,
    description := "Scala.meta's abstract syntax trees",
    // NOTE: uncomment this to update ast.md
    // scalacOptions += "-Xprint:typer",
    enableMacros
  )
  .dependsOn(common, dialects, inputs, tokens, tokenizers) // NOTE: tokenizers needed for Tree.tokens when Tree.pos.isEmpty
lazy val treesJVM = trees.jvm
lazy val treesJS = trees.js

lazy val semantic = crossProject
  .in(file("scalameta/semantic"))
  .settings(
    publishableSettings,
    description := "Scala.meta's semantic APIs",
    // Protobuf setup for binary serialization.
    PB.targets.in(Compile) := Seq(
      scalapb.gen(
        flatPackage = true // Don't append filename to package
      ) -> sourceManaged.in(Compile).value
    ),
    PB.protoSources.in(Compile) := Seq(file("scalameta/semantic/shared/src/main/protobuf")),
    libraryDependencies += "com.trueaccord.scalapb" %%% "scalapb-runtime" % scalapbVersion
  )
  .dependsOn(common, trees)
lazy val semanticJVM = semantic.jvm
lazy val semanticJS = semantic.js

lazy val scalameta = crossProject
  .in(file("scalameta/scalameta"))
  .settings(
    publishableSettings,
    description := "Scala.meta's metaprogramming APIs",
    exposePaths("scalameta", Test)
  )
  .dependsOn(
    common,
    dialects,
    parsers,
    quasiquotes,
    tokenizers,
    transversers,
    trees,
    inline,
    semantic
  )
lazy val scalametaJVM = scalameta.jvm
lazy val scalametaJS = scalameta.js

lazy val scalahost = project
  .in(file("scalahost/core"))
  .settings(
    moduleName := "scalahost",
    description := "Scala.meta semantic API integration for Scala 2.x (scalac).",
    publishableSettings,
    hasLargeIntegrationTests,
    isFullCrossVersion,
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
  )
  .dependsOn(scalametaJVM)

lazy val scalahostNsc = project
  .in(file("scalahost/nsc"))
  .settings(
    moduleName := "scalahost-nsc",
    description := "Scala 2.x compiler plugin that persists the semantic DB on compile.",
    publishableSettings,
    mergeSettings,
    isFullCrossVersion,
    exposePaths("scalahost", Test),
    pomPostProcess := { node =>
      new RuleTransformer(new RewriteRule {
        private def isScalametaDependency(node: XmlNode): Boolean = {
          def isArtifactId(node: XmlNode, fn: String => Boolean) =
            node.label == "artifactId" && fn(node.text)
          node.label == "dependency" && node.child.exists(child =>
            isArtifactId(child, _.startsWith("scalameta_")))
        }
        override def transform(node: XmlNode): XmlNodeSeq = node match {
          case e: Elem if isScalametaDependency(node) =>
            Comment("scalameta dependency has been merged into scalahost via sbt-assembly")
          case _ => node
        }
      }).transform(node).head
    }
  )
  .dependsOn(scalahost, testkit % Test)

lazy val scalahostSbt = project
  .in(file("scalahost/sbt"))
  .settings(
    publishableSettings,
    buildInfoSettings,
    sbt.ScriptedPlugin.scriptedSettings,
    sbtPlugin := true,
    bintrayRepository := "maven", // sbtPlugin overrides this to sbt-plugins
    testQuick := {
      // runs tests for 2.11 only, avoiding the need to publish for 2.12
      RunSbtCommand(s"; plz $ScalaVersion publishLocal " +
        "; such scalahostSbt/scripted sbt-scalahost/semantic-example")(state.value)
    },
    test := {
      RunSbtCommand("; such publishLocal " +
        "; such scalahostSbt/scripted")(state.value)
    },
    description := "sbt plugin to enable the scalahost compiler plugin",
    moduleName := "sbt-scalahost", // sbt convention is that plugin names start with sbt-
    scalaVersion := "2.10.6",
    crossScalaVersions := Seq("2.10.6"), // for some reason, scalaVersion.value does not work.
    scriptedLaunchOpts ++= Seq(
      "-Dplugin.version=" + version.value,
      // .jvmopts is ignored, simulate here
      "-XX:MaxPermSize=256m",
      "-Xmx2g",
      "-Xss2m"
    ) ++ {
      // pass along custom boot properties if specified
      val bootProps = "sbt.boot.properties"
      sys.props.get(bootProps).map(x => s"-D$bootProps=$x").toList
    },
    scriptedBufferLog := false
  )
  .enablePlugins(BuildInfoPlugin)

lazy val testkit = Project(id = "testkit", base = file("scalameta/testkit"))
  .settings(
    publishableSettings,
    hasLargeIntegrationTests,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "geny" % "0.1.1",
      // These are used to download and extract a corpus tar.gz
      "org.rauschig" % "jarchivelib" % "0.7.1",
      "commons-io" % "commons-io" % "2.5"
    ),
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test,
    description := "Testing utilities for scala.meta's metaprogramming APIs"
  )
  .dependsOn(scalametaJVM)

lazy val tests = project
  .in(file("scalameta/tests"))
  .settings(
    sharedSettings,
    nonPublishableSettings,
    description := "Runs all tests in scalameta project.",
    test := {
      testJVM.value
      testJS.value
    },
    // These tasks skip over modules with no tests, like dialects/inputs/io, speeding up
    // edit/test cycles. You may prefer to run testJVM while iterating on a design
    // because JVM tests link and run faster than JS tests.
    testJVM := {
      val runScalametaTests = test.in(scalametaJVM, Test).value
      val runScalahostTests = test.in(scalahostNsc, Test).value
      val runBenchmarkTests = test.in(benchmarks, Test).value
      val runContribTests = test.in(contribJVM, Test).value
      val runDocs = test.in(readme).value
    },
    testJS := {
      val runScalametaTests = test.in(scalametaJS, Test).value
      val runContribTests = test.in(contribJS, Test).value
    }
  )
lazy val testJVM = taskKey[Unit]("Run JVM tests")
lazy val testJS = taskKey[Unit]("Run Scala.js tests")

lazy val contrib = crossProject
  .in(file("scalameta/contrib"))
  .settings(
    publishableSettings,
    description := "Utilities for scala.meta"
  )
  .jvmConfigure(_.dependsOn(testkit))
  .dependsOn(scalameta)
lazy val contribJVM = contrib.jvm
lazy val contribJS = contrib.js

lazy val benchmarks =
  Project(id = "benchmarks", base = file("scalameta/benchmarks"))
    .settings(
      sharedSettings,
      nonPublishableSettings,
      resourceDirectory.in(Jmh) := resourceDirectory.in(Compile).value,
      javaOptions.in(run) ++= Seq(
        "-Djava.net.preferIPv4Stack=true",
        "-XX:+AggressiveOpts",
        "-XX:+UseParNewGC",
        "-XX:+UseConcMarkSweepGC",
        "-XX:+CMSParallelRemarkEnabled",
        "-XX:+CMSClassUnloadingEnabled",
        "-XX:ReservedCodeCacheSize=128m",
        "-XX:MaxPermSize=1024m",
        "-Xss8M",
        "-Xms512M",
        "-XX:SurvivorRatio=128",
        "-XX:MaxTenuringThreshold=0",
        "-Xss8M",
        "-Xms512M",
        "-Xmx2G",
        "-server"
      )
    )
    .dependsOn(scalametaJVM)
    .enablePlugins(JmhPlugin)

lazy val readme = scalatex
  .ScalatexReadme(
    projectId = "readme",
    wd = file(""),
    url = "https://github.com/scalameta/scalameta/tree/master",
    source = "Readme"
  )
  .settings(
    sharedSettings,
    nonPublishableSettings,
    exposePaths("readme", Runtime),
    crossScalaVersions := LanguageVersions, // No need to cross-build.
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    sources.in(Compile) ++= List("os.scala").map(f => baseDirectory.value / "../project" / f),
    watchSources ++= baseDirectory.value.listFiles.toList,
    test := run.in(Compile).toTask(" --validate").value,
    publish := {
      if (sys.props("sbt.prohibit.publish") != null)
        sys.error("Undefined publishing strategy")

      // generate the scalatex readme into `website`
      val website =
        new File(target.value.getAbsolutePath + File.separator + "scalatex")
      if (website.exists) website.delete
      val _ = run.in(Compile).toTask(" --validate").value
      if (!website.exists) sys.error("failed to generate the scalatex website")

      // import the scalatex readme into `repo`
      val repo = new File(os.temp.mkdir.getAbsolutePath + File.separator + "scalameta.org")
      os.shell.call(s"git clone https://github.com/scalameta/scalameta.github.com ${repo.getAbsolutePath}")
      println(s"erasing everything in ${repo.getAbsolutePath}...")
      repo.listFiles.filter(f => f.getName != ".git").foreach(os.shutil.rmtree)
      println(s"importing website from ${website.getAbsolutePath} to ${repo.getAbsolutePath}...")
      new PrintWriter(new File(repo.getAbsolutePath + File.separator + "CNAME")) {
        write("scalameta.org"); close
      }
      website.listFiles.foreach(src =>
        os.shutil.copytree(src, new File(repo.getAbsolutePath + File.separator + src.getName)))

      // commit and push the changes if any
      os.shell.call(s"git add -A", cwd = repo.getAbsolutePath)
      val nothingToCommit = "nothing to commit, working directory clean"
      try {
        val url = "https://github.com/scalameta/scalameta/tree/" + os.git.currentSha()
        os.shell.call(s"git config user.email 'scalametabot@gmail.com'", cwd = repo.getAbsolutePath)
        os.shell.call(s"git config user.name 'Scalameta Bot'", cwd = repo.getAbsolutePath)
        os.shell.call(s"git commit -m $url", cwd = repo.getAbsolutePath)
        os.secret.obtain("github").foreach {
          case (username, password) =>
            val httpAuthentication = s"$username:$password@"
            val authUrl = s"https://${httpAuthentication}github.com/scalameta/scalameta.github.com"
            os.shell.call(s"git push $authUrl master", cwd = repo.getAbsolutePath)
        }
      } catch {
        case ex: Exception if ex.getMessage.contains(nothingToCommit) =>
          println(nothingToCommit)
      }
    },
    publishLocal := {},
    publishM2 := {}
  )
  .dependsOn(scalametaJVM)

// ==========================================
// Settings
// ==========================================

lazy val sharedSettings = Def.settings(
  scalaVersion := LanguageVersion,
  crossScalaVersions := LanguageVersions,
  crossVersion := {
    crossVersion.value match {
      case old @ ScalaJSCrossVersion.binary => old
      case _ => CrossVersion.binary
    }
  },
  version := LibraryVersion,
  organization := "org.scalameta",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.1" % "test",
  libraryDependencies += "org.scalacheck" %%% "scalacheck" % "1.13.5" % "test",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  scalacOptions.in(Compile, doc) ++= Seq("-skip-packages", ""),
  scalacOptions.in(Compile, doc) ++= Seq("-implicits", "-implicits-hide:."),
  scalacOptions.in(Compile, doc) ++= Seq("-groups"),
  scalacOptions ++= Seq("-Xfatal-warnings"),
  parallelExecution.in(Test) := false, // hello, reflection sync!!
  logBuffered := false,
  updateOptions := updateOptions.value.withCachedResolution(true),
  triggeredMessage.in(ThisBuild) := Watched.clearWhenTriggered
)

lazy val mergeSettings = Def.settings(
  sharedSettings,
  test.in(assembly) := {},
  logLevel.in(assembly) := Level.Error,
  assemblyJarName.in(assembly) :=
    name.value + "_" + scalaVersion.value + "-" + version.value + "-assembly.jar",
  assemblyOption.in(assembly) ~= { _.copy(includeScala = false) },
  Keys.`package`.in(Compile) := {
    val slimJar = Keys.`package`.in(Compile).value
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    slimJar
  },
  packagedArtifact.in(Compile).in(packageBin) := {
    val temp = packagedArtifact.in(Compile).in(packageBin).value
    val (art, slimJar) = temp
    val fatJar =
      new File(crossTarget.value + "/" + assemblyJarName.in(assembly).value)
    val _ = assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
    (art, slimJar)
  }
)

lazy val publishableSettings = Def.settings(
  sharedSettings,
  bintrayOrganization := Some("scalameta"),
  publishArtifact.in(Compile) := true,
  publishArtifact.in(Test) := false,
  {
    val publishingEnabled: Boolean = {
      if (!new File(sys.props("user.home") + "/.bintray/.credentials").exists) false
      else if (sys.props("sbt.prohibit.publish") != null) false
      else if (sys.env.contains("CI_PULL_REQUEST")) false
      else true
    }
    if (sys.props("disable.publish.status") == null) {
      sys.props("disable.publish.status") = ""
      val publishingStatus = if (publishingEnabled) "enabled" else "disabled"
      println(s"[info] Welcome to scala.meta $LibraryVersion (publishing $publishingStatus)")
    }
    publish.in(Compile) := {
      if (publishingEnabled) {
        Def.task {
          publish.value
        }
      } else {
        Def.task {
          sys.error("Undefined publishing strategy"); ()
        }
      }
    }
  },
  publishMavenStyle := true,
  pomIncludeRepository := { x =>
    false
  },
  licenses += "BSD" -> url("https://github.com/scalameta/scalameta/blob/master/LICENSE.md"),
  pomExtra := (
    <url>https://github.com/scalameta/scalameta</url>
    <inceptionYear>2014</inceptionYear>
    <scm>
      <url>git://github.com/scalameta/scalameta.git</url>
      <connection>scm:git:git://github.com/scalameta/scalameta.git</connection>
    </scm>
    <issueManagement>
      <system>GitHub</system>
      <url>https://github.com/scalameta/scalameta/issues</url>
    </issueManagement>
    <developers>
      <developer>
        <id>xeno-by</id>
        <name>Eugene Burmako</name>
        <url>http://xeno.by</url>
      </developer>
      <developer>
        <id>densh</id>
        <name>Denys Shabalin</name>
        <url>http://den.sh</url>
      </developer>
      <developer>
        <id>olafurpg</id>
        <name>Ólafur Páll Geirsson</name>
        <url>https://geirsson.com/</url>
      </developer>
    </developers>
  )
)

lazy val nonPublishableSettings = Seq(
  publishArtifact := false,
  publish := {}
)

lazy val buildInfoSettings = Def.settings(
  buildInfoKeys := Seq[BuildInfoKey](
    version,
    "supportedScalaVersions" -> crossScalaVersions.in(scalametaJVM).value
  ),
  buildInfoPackage := "org.scalameta",
  buildInfoObject := "BuildInfo"
)

lazy val isFullCrossVersion = Seq(
  crossVersion := CrossVersion.full,
  unmanagedSourceDirectories.in(Compile) += {
    // NOTE: sbt 0.13.8 provides cross-version support for Scala sources
    // (http://www.scala-sbt.org/0.13/docs/sbt-0.13-Tech-Previews.html#Cross-version+support+for+Scala+sources).
    // Unfortunately, it only includes directories like "scala_2.11" or "scala_2.12",
    // not "scala_2.11.8" or "scala_2.12.1" that we need.
    // That's why we have to work around here.
    val base = sourceDirectory.in(Compile).value
    base / ("scala-" + scalaVersion.value)
  }
)

lazy val hasLargeIntegrationTests = Seq(
  fork in (Test, run) := true,
  javaOptions in (Test, run) += "-Xss4m"
)

def exposePaths(projectName: String, config: Configuration) = {
  def uncapitalize(s: String) =
    if (s.length == 0) ""
    else {
      val chars = s.toCharArray
      chars(0) = chars(0).toLower
      new String(chars)
    }
  val prefix = "sbt.paths." + projectName + "." + uncapitalize(config.name) + "."
  Seq(
    sourceDirectory.in(config) := {
      val defaultValue = sourceDirectory.in(config).value
      System.setProperty(prefix + "sources", defaultValue.getAbsolutePath)
      defaultValue
    },
    resourceDirectory.in(config) := {
      val defaultValue = resourceDirectory.in(config).value
      System.setProperty(prefix + "resources", defaultValue.getAbsolutePath)
      defaultValue
    },
    fullClasspath.in(config) := {
      val defaultValue = fullClasspath.in(config).value
      val classpath = defaultValue.files.map(_.getAbsolutePath)
      val scalaLibrary =
        classpath.map(_.toString).find(_.contains("scala-library")).get
      System.setProperty("sbt.paths.scalalibrary.classes", scalaLibrary)
      System.setProperty(prefix + "classes", classpath.mkString(java.io.File.pathSeparator))
      defaultValue
    }
  )
}

lazy val enableMacros = macroDependencies(hardcore = false)

lazy val enableHardcoreMacros = macroDependencies(hardcore = true)

def macroDependencies(hardcore: Boolean) = libraryDependencies ++= {
  val scalaReflect =
    Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided")
  val scalaCompiler = {
    if (hardcore)
      Seq("org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided")
    else Nil
  }
  val backwardCompat210 = {
    if (scalaVersion.value.startsWith("2.10"))
      Seq("org.scalamacros" %% "quasiquotes" % "2.1.0")
    else Seq()
  }
  scalaReflect ++ scalaCompiler ++ backwardCompat210
}

lazy val ciScalaVersion = sys.env("CI_SCALA_VERSION")
def CiCommand(name: String)(commands: List[String]): Command = Command.command(name) { initState =>
  commands.foldLeft(initState) {
    case (state, command) => ci(command) :: state
  }
}
def ci(command: String) = s"plz $ciScalaVersion $command"

