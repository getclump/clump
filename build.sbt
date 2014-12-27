name := "clump"

scalaVersion := "2.10.4"

libraryDependencies += "org.specs2" %% "specs2" % "2.4.2" % "test"

libraryDependencies += "org.mockito" % "mockito-core" % "1.9.5" % "test"

libraryDependencies += "com.twitter" %% "util-core" % "6.22.0"

ScoverageSbtPlugin.instrumentSettings

CoverallsPlugin.coverallsSettings