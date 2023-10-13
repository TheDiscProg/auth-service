import sbt._

object Dependencies {

  lazy val dapexMessagingVersion = "0.1.1"
  lazy val dapexMessagingRepo = RootProject(uri("https://github.com/TheDiscProg/dapex-messaging.git#%s".format(dapexMessagingVersion)))

  lazy val all = Seq(
    "DAPEX" %% "dapex-messaging" % "0.1.6",
    "Shareprice" %% "shareprice-config" % "0.1.2",
    "DAPEX" %% "dapex-rabbitmq" % "0.1.6",
    "org.typelevel" %% "cats-effect" % "3.4.8",
    "org.http4s" %% "http4s-dsl" % "0.23.18",
    "org.http4s" %% "http4s-ember-server" % "0.23.18",
    "org.http4s" %% "http4s-ember-client" % "0.23.18",
    "org.http4s" %% "http4s-circe" % "0.23.18",
    "io.circe" %% "circe-core" % "0.14.5",
    "io.circe" %% "circe-generic" % "0.14.5",
    "io.circe" %% "circe-parser" % "0.14.5",
    "io.circe" %% "circe-refined" % "0.14.5",
    "io.circe" %% "circe-generic-extras" % "0.14.3",
    "io.circe" %% "circe-config" % "0.10.0",
    "eu.timepit" %% "refined" % "0.10.2",
    "ch.qos.logback" % "logback-classic" % "1.4.6",
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
    "org.scalactic" %% "scalactic" % "3.2.15",
    "org.scalatest" %% "scalatest" % "3.2.15" % Test,
    "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test,
    "com.beachape" %% "enumeratum" % "1.7.2",
    "com.beachape" %% "enumeratum-circe" % "1.7.2",
    "io.scalaland" %% "chimney" % "0.7.4",
    "com.hazelcast" % "hazelcast" % "5.3.1",
    "com.github.jwt-scala" %% "jwt-circe" % "9.3.0",
    "dev.profunktor" %% "http4s-jwt-auth" % "1.2.0",
    "com.google.guava" % "guava" % "32.0.0-jre",
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test
  )
}
