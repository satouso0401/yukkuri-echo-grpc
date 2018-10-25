name := "yukkuri-grpc-echo"

version := "0.1"

scalaVersion := "2.12.6"

import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

PB.protocVersion := "-v351"
libraryDependencies += "io.grpc" % "grpc-netty-shaded" % grpcJavaVersion
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf"
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion

PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value)

scalacOptions ++= (
  "-deprecation" ::
    "-Xlint" ::
    Nil
  )
