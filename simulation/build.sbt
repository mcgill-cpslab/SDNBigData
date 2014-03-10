name := "scalaflowsim"

version := "1.0"

libraryDependencies ++= Seq(
			"org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
			"ch.qos.logback" % "logback-classic" % "1.0.13",
			"io.netty" % "netty" % "3.6.6.Final")
			            
resolvers ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation")

parallelExecution in Test := false
