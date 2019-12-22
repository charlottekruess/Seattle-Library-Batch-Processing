name := "BIDM_F19_Exam"

version := "0.1"

scalaVersion := "2.11.12"

cleanupCommands in console := "spark.stop()"
cleanupCommands :=  "spark.stop()"


scalacOptions ++= Seq("-unchecked", "-deprecation")

val sparkVersion = "2.4.4"

libraryDependencies += "org.apache.spark" %% "spark-core"  % sparkVersion  % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql"   % sparkVersion  % "provided"
libraryDependencies += "org.apache.spark" %% "spark-yarn"  % sparkVersion  % "provided"


libraryDependencies += "com.typesafe" % "config" % "1.3.0"
logBuffered in Test := false


assemblyMergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf")          => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$")      => MergeStrategy.discard
  case "log4j.properties"                                  => MergeStrategy.discard
  case m if m.toLowerCase.startsWith("meta-inf/services/") => MergeStrategy.filterDistinctLines
  case "reference.conf"                                    => MergeStrategy.concat
  case _                                                   => MergeStrategy.first
}

