name := "http4s-demo"

version := "0.1"

scalaVersion := "2.12.8"

fork := true

scalacOptions := Seq("-feature", "-encoding", "utf8",
  "-deprecation", "-unchecked", "-Xlint", "-Yrangepos", "-Ypartial-unification", "-explaintypes")

val scalaLoggingVersion = "3.9.0"
val logBackVersion = "1.2.3"
val scallopVersion = "3.1.2"
val configVersion = "1.3.3"
val http4sVersion = "0.20.0-M6"
val circeVersion = "0.11.1"
val slickVersion = "3.3.0"
val slickPgVersion = "0.17.2"
val specs2Version = "4.0.2"
val catsParVersion = "0.2.1"
val awsSdkVersion = "2.4.10"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
  "commons-io" % "commons-io" % "2.6",
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "ch.qos.logback"       % "logback-classic"      % logBackVersion % "runtime",
  "org.codehaus.janino"  % "janino"               % "3.0.9",
  "org.rogach"          %% "scallop"              % scallopVersion,
  "com.typesafe"         % "config"               % configVersion,
  "org.apache.commons"   % "commons-text"         % "1.6",
  "org.http4s"          %% "http4s-dsl"           % http4sVersion,
  "org.http4s"          %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"          %% "http4s-async-http-client" % http4sVersion,
  "org.http4s"          %% "http4s-circe"         % http4sVersion,
  "io.circe"            %% "circe-generic"        % circeVersion,
  "io.circe"            %% "circe-generic-extras" % circeVersion,
  "io.circe"            %% "circe-literal"        % circeVersion,
  "com.typesafe.slick"  %% "slick"                % slickVersion,
  "com.typesafe.slick"  %% "slick-hikaricp"       % slickVersion,
  "com.github.tminglei" %% "slick-pg"             % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_circe-json"  % slickPgVersion,
  "io.chrisdavenport"   %% "cats-par"             % catsParVersion,
  "software.amazon.awssdk"        % "lambda"      % awsSdkVersion,
  "software.amazon.awssdk"        % "signer"      % awsSdkVersion,
  "org.specs2"          %% "specs2-core"          % specs2Version % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

