lazy val akkaHttpVersion   = "$akka_http_version$"
lazy val akkaVersion       = "$akka_version$"
lazy val catsCoreVersion   = "$cats_core_version$"
lazy val logbackVersion    = "$logback_version$"
lazy val logstashVersion   = "$logstash_version$"
lazy val scalaTestVersion  = "$scala_test_version$"
lazy val mockitoVersion    = "$mockito_version$"
lazy val scalaCheckVersion = "$scala_check_version$"
lazy val fasterXmlVersion  = "$faster_xml_version$"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "$organization$",
      scalaVersion    := "$scala_version$",
      fork := true
    )),
    name := "$name$",
    maintainer in Docker := "$docker_maintainer$",
    packageName in Docker := "$docker_package_name$",
    dockerExposedPorts ++= Seq(9000),
    dockerBaseImage := "$docker_base_image$",
    git.formattedShaVersion := git.gitHeadCommit.value map { sha => s"\$sha".take(7) },
    libraryDependencies ++= Seq(
      "com.typesafe.akka"            %% "akka-http"                 % akkaHttpVersion,
      "com.typesafe.akka"            %% "akka-http-spray-json"      % akkaHttpVersion,
      "com.typesafe.akka"            %% "akka-actor-typed"          % akkaVersion,
      "com.typesafe.akka"            %% "akka-stream"               % akkaVersion,
      "ch.qos.logback"                % "logback-classic"           % logbackVersion,
      "net.logstash.logback"          % "logstash-logback-encoder"  % logstashVersion,
      "org.typelevel"                %% "cats-core"                 % catsCoreVersion,
      "com.typesafe.akka"            %% "akka-http-testkit"         % akkaHttpVersion   % Test,
      "com.typesafe.akka"            %% "akka-actor-testkit-typed"  % akkaVersion       % Test,
      "com.typesafe.akka"            %% "akka-stream-testkit"       % akkaVersion       % Test,
      "org.mockito"                   % "mockito-core"              % mockitoVersion    % Test,
      "org.scalacheck"               %% "scalacheck"                % scalaCheckVersion % Test,
      "org.scalatest"                %% "scalatest"                 % scalaTestVersion  % Test,

      // Only needed to json marshal objects into logging directives
      "com.fasterxml.jackson.module"  % "jackson-module-scala_2.13" % fasterXmlVersion
),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-unchecked",
      "-deprecation",
      "-Wdead-code",
      "-Wnumeric-widen",
      "-Wvalue-discard",
      "-Xfatal-warnings",
      "-Xlint:unused",
      "-Xlint:nonlocal-return",
      "-Xlint:deprecation",
      "-Wconf:src=src_managed/.*:s"
    )
  )
  .enablePlugins(GitVersioning)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)
  // Required to use 'sh' instead of `bash` with Alpine dist
  .enablePlugins(AshScriptPlugin)


