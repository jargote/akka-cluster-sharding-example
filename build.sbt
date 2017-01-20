import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

val project = Project(
  base = file("."),
  settings = Defaults.coreDefaultSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
    name := """distributed-contacts-directory""",
    version := "1.0",
    scalaVersion := "2.11.8",
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.9",
      "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9",
      "com.typesafe.akka" %% "akka-cluster" % "2.4.9",
      "com.typesafe.akka" %% "akka-cluster-sharding" % "2.4.9",
      "com.typesafe.akka" %% "akka-persistence" % "2.4.9",
      "com.typesafe.akka" %% "akka-remote" % "2.4.9",
      "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.9",
      "com.typesafe.akka" %% "akka-testkit" % "2.4.9" % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.4.9",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.9",
      "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.3.7",
      "org.typelevel"       %% "cats" % "0.6.0",
      "ch.qos.logback"      %  "logback-classic" % "1.1.2",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"),
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )
) configs (MultiJvm)
