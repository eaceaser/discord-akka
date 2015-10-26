name := "discord-akka"

version := "0.1.0"

scalaVersion := "2.11.7"

val libraryVersion = "1.2.0-M1" // or "1.3.0-SNAPSHOT"

libraryDependencies := Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-http-experimental" % "1.0-SNAPSHOT",
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0-SNAPSHOT",
  "com.github.julien-truffaut"  %%  "monocle-core"    % libraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-generic" % libraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-macro"   % libraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-state"   % libraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-law"     % libraryVersion % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)
