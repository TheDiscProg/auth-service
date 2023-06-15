package dapex

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import com.comcast.ip4s._
import dapex.authorc.domain.caching.AuthenticationCachingService
import dapex.authorc.domain.caching.hcast.AuthHzcMaps
import dapex.authorc.domain.orchestrator.AuthenticationOrchestrator
import dapex.authorc.domain.rabbitmq.Rabbit
import dapex.authorc.domain.rabbitmq.consumer.{
  DapexMessageRouter,
  DapexMessageRouterAlgebra,
  RabbitDapexMessageConsumer
}
import dapex.authorc.domain.rabbitmq.publisher.DapexMQPublisher
import dapex.authorc.domain.security.{HashingService, JwtSecurityTokenService}
import dapex.config.ServerConfiguration
import dapex.guardrail.healthcheck.HealthcheckResource
import dapex.server.domain.healthcheck.{
  HealthCheckService,
  HealthChecker,
  HealthcheckAPIHandler,
  SelfHealthCheck
}
import dev.profunktor.fs2rabbit.effects.Log
import dev.profunktor.fs2rabbit.resiliency.ResilientStream
import io.circe.config.parser
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.{Logger => Log4CatsLogger}

object AppServer {

  def createServer[F[
      _
  ]: Async: Log4CatsLogger: Parallel](): Resource[F, (Server, DapexMessageRouterAlgebra[F])] =
    for {
      conf <- Resource.eval(parser.decodePathF[F, ServerConfiguration](path = "server"))

      // RabbitMQ Client and publisher
      rmqDispatcher <- Dispatcher.parallel
      rmqClient <- Resource.eval(Rabbit.getRabbitClient(conf.rabbitMQ, rmqDispatcher))
      rmqPublisher = new DapexMQPublisher[F](rmqClient)

      // Hazelcast Caching Service
      authMaps = AuthHzcMaps[F](conf.caching)
      authenticationCachingService = AuthenticationCachingService[F](authMaps)

      // Security Services
      hashingService = HashingService()
      tokenService = JwtSecurityTokenService(conf)

      // Orchestrator
      orchestrator = AuthenticationOrchestrator[F](
        authenticationCachingService,
        rmqPublisher,
        hashingService,
        tokenService
      )

      // RabbitMQ Consumer and Router
      aMQPChannel <- rmqClient.createConnectionChannel
      rabbitConsumer = RabbitDapexMessageConsumer[F](rmqClient, aMQPChannel)
      rabbitMessageRouter = DapexMessageRouter[F](rabbitConsumer.consumers, orchestrator)

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
      server: Server <- EmberServerBuilder.default
        .withPort(httpPort.getOrElse(port"8000"))
        .withHost(httpHost.getOrElse(ipv4"0.0.0.0"))
        .withHttpApp(httpApp)
        .build
    } yield (server, rabbitMessageRouter)

  def processRMQMessages[F[_]: Log: Temporal](
      dapexMessageRouter: DapexMessageRouterAlgebra[F]
  ): F[ExitCode] =
    ResilientStream.run(dapexMessageRouter.flow).as(ExitCode.Success)
}
