import com.typesafe.sbt.pgp.PgpKeys
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin._

object Build extends Build {
  val commonSettings = Seq(
    organization := "io.getclump",
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.5"),
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2" % "2.4.2" % "test",
      "org.mockito" % "mockito-core" % "1.9.5" % "test"
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:reflectiveCalls"
    )
  ) ++ releaseSettings ++ Seq(
    ReleaseKeys.crossBuild := true,
    ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra :=
      <url>http://github.com/getclump/clump</url>
      <licenses>
        <license>
          <name>LGPL</name>
          <url>https://raw.githubusercontent.com/getclump/clump/master/LICENSE-LGPL.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:getclump/clump.git</url>
        <connection>scm:git:git@github.com:getclump/clump.git</connection>
      </scm>
      <developers>
        <developer>
          <id>fwbrasil</id>
          <name>Flavio W. Brasil</name>
          <url>http://github.com/fwbrasil/</url>
        </developer>
        <developer>
          <id>williamboxhall</id>
          <name>William Boxhall</name>
          <url>http://github.com/williamboxhall/</url>
        </developer>
      </developers>
  )

  lazy val clumpScala = Project(id = "clump-scala", base = file("."))
    .settings(name := "clump-scala")
    .settings(commonSettings: _*)
    .settings(target <<= target(_ / "clump-scala"))
//    .aggregate(clumpTwitter)

  lazy val clumpTwitter = Project(id = "clump-twitter", base = file("."))
    .settings(name := "clump-twitter")
    .settings(commonSettings: _*)
    .settings(libraryDependencies += "com.twitter" %% "util-core" % "6.22.0")
    .settings(target <<= target(_ / "clump-twitter"))
    .settings(excludeFilter in unmanagedSources := "package.scala")
    .settings(sourceGenerators in Compile += Def.task {
      val source = sourceDirectory.value / "main" / "scala" / "io" / "getclump" / "package-twitter.scala.tmpl"
      val file = sourceManaged.value / "main" / "scala" / "io" / "getclump" / "package.scala"
      IO.copyFile(source, file)
      Seq(file)
    }.taskValue)
}
