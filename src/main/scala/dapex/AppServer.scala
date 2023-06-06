package dapex

import cats.data.NonEmptyList
import cats.effect.{Async, Resource, Sync}
import cats.{Applicative, Monad, MonadError, Parallel}
import com.comcast.ip4s._
import dapex.config.ServerConfiguration
import dapex.guardrail.healthcheck.HealthcheckResource
import dapex.server.domain.healthcheck.{HealthCheckService, HealthChecker, HealthcheckAPIHandler, SelfHealthCheck}
import io.circe.config.parser
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.{Logger => Log4CatsLogger}

object AppServer {

  def createServer[F[
      _
  ]: Monad: Async: Log4CatsLogger: Parallel: Applicative: Sync: MonadError[*[_], Throwable]](): Resource[F, Server] =
    for {
      conf <- Resource.eval(parser.decodePathF[F, ServerConfiguration](path = "server"))

      // Health checkers
      checkers = NonEmptyList.of[HealthChecker[F]](SelfHealthCheck[F])
      healthCheckers = HealthCheckService(checkers)
      healthRoutes = new HealthcheckResource().routes(
        new HealthcheckAPIHandler[F](healthCheckers)
      )

      // Routes and HTTP App
      allRoutes = healthRoutes.orNotFound
      httpApp = Logger.httpApp(logHeaders = true, logBody = true)(allRoutes)

      // Build server
      httpPort = Port.fromInt(conf.http.port.value)
      httpHost = Ipv4Address.fromString(conf.http.host.value)
      server <- EmberServerBuilder.default
        .withPort(httpPort.getOrElse(port"8000"))
        .withHost(httpHost.getOrElse(ipv4"0.0.0.0"))
        .withHttpApp(httpApp)
        .build
    } yield server
}
