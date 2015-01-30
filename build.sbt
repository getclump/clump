name := "clump"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.5")

libraryDependencies += "org.specs2" %% "specs2" % "2.4.2" % "test"

libraryDependencies += "org.mockito" % "mockito-core" % "1.9.5" % "test"

libraryDependencies += "com.twitter" %% "util-core" % "6.22.0"

releaseSettings

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>http://github.com/fwbrasil/clump</url>
  <licenses>
    <license>
      <name>LGPL</name>
      <url>LICENCE-LGPL.md</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:fwbrasil/clump.git</url>
    <connection>scm:git:git@github.com:fwbrasil/clump.git</connection>
  </scm>
  <developers>
    <developer>
      <id>fwbrasil</id>
      <name>Flavio W. Brasil</name>
      <url>http://fwbrasil.net</url>
    </developer>
    <developer>
      <id>williamboxhall</id>
      <name>William Boxhall</name>
      <url></url>
    </developer>
  </developers>)