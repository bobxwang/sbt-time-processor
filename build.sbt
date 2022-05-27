name := "process"

version := "0.1"

scalaVersion := "2.12.8"

organization := "com.bob.xxx"

assemblyJarName in assembly := "process-0.0.1.jar"

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

// adding the tools.jar to the unmanaged-jars seq
unmanagedJars in Compile ~= { uj =>
  Seq(Attributed.blank(file(System.getProperty("java.home").dropRight(3) + "lib/tools.jar"))) ++ uj
}

// 打包时排除指定包
assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter {
    _.data.getName == "tools.jar"
  }
}

// 合并策略
assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs@_*) => MergeStrategy.first
  case PathList(ps@_*) if ps.last endsWith "UnusedStubClass.class" => MergeStrategy.first
  case PathList(ps@_*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf" => MergeStrategy.concat
  case "unwanted.txt" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}