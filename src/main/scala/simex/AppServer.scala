package simex

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Dispatcher
import com.comcast.ip4s._
import io.circe.config.parser
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.{Logger => Log4CatsLogger}
import shareprice.caching.CachingMap
import shareprice.config.ServerConfiguration
import shareprice.kafka.SharepriceTopic
import simex.authorc.domain.orchestrator.AuthenticationOrchestrator
import simex.authorc.domain.registration.UserRegistrationService
import simex.authorc.domain.rmqconsumer.{RMQMessageHandler, RMQMessageRouter}
import simex.caching.CachingService
import simex.caching.config.HazelcastConfig
import simex.guardrail.healthcheck.HealthcheckResource
import simex.kafka.KafkaConfigurator
import simex.kafka.config.KafkaConfig
import simex.kafka.producer.SimexKafkaProducer
import simex.rabbitmq.Rabbit
import simex.rabbitmq.consumer.SimexMessageHandler
import simex.rabbitmq.publisher.SimexMQPublisher
import simex.server.domain.healthcheck.{
  HealthCheckService,
  HealthChecker,
  HealthcheckAPIHandler,
  SelfHealthCheck
}
import simex.server.entities.AppService
import thediscprog.utillibrary.hashing.HashingService
import thediscprog.utillibrary.jwt.SecurityTokenService

object AppServer {

  def createServer[F[
      _
  ]: Async: Log4CatsLogger: Parallel](): Resource[F, AppService[F]] =
    for {
      conf <- Resource.eval(parser.decodePathF[F, ServerConfiguration](path = "server"))

      // RabbitMQ Client and publisher
      rmqDispatcher <- Dispatcher.parallel
      rmqClient <- Resource.eval(Rabbit.getRabbitClient(conf.rabbitMQ, rmqDispatcher))
      rmqPublisher = new SimexMQPublisher[F](rmqClient)

      // Hazelcast Caching Service
      cachingConfig: HazelcastConfig = conf.caching match {
        case Some(config) => config
        case None =>
          HazelcastConfig(
            clusterName = "shareprice",
            clusterAddress = "localhost",
            ports = "5701",
            outwardPort = "4700-34710",
            authTokenTTL = 0
          )
      }
      requestCachingService = CachingService[F](
        cachingConfig.copy(authTokenTTL = CachingMap.RequestAuthentication.ttl),
        CachingMap.RequestAuthentication.name
      )
      authTokenCachingService = CachingService[F](
        cachingConfig.copy(authTokenTTL = CachingMap.AuthenticationAuthorisationToken.ttl),
        CachingMap.AuthenticationAuthorisationToken.name
      )
      refreshTokenCachingService = CachingService[F](
        cachingConfig.copy(authTokenTTL = CachingMap.AuthenticationRefreshToken.ttl),
        CachingMap.AuthenticationRefreshToken.name
      )

      // Security Services
      hashingService = HashingService()
      tokenService = SecurityTokenService(conf.tokenKey)

      // Kafka create topics etc
      kafkaConfig = conf.kafka match {
        case Some(config) => config
        case None =>
          KafkaConfig(
            bootstrapServer = "localhost",
            port = 9092,
            group = "shareprice"
          )
      }
      _ <- Resource.eval(
        KafkaConfigurator.createTopicIfNotExists(SharepriceTopic.DB_WRITE, kafkaConfig)
      )
      kafkaProducer = SimexKafkaProducer[F](kafkaConfig)

      // Registration Service
      userRegistrationService = UserRegistrationService(rmqPublisher, kafkaProducer, hashingService)

      // Orchestrator
      orchestrator = AuthenticationOrchestrator[F](
        rmqPublisher,
        hashingService,
        tokenService,
        requestCachingService,
        authTokenCachingService,
        refreshTokenCachingService,
        userRegistrationService
      )

      // RabbitMQ Consumer and Router
      aMQPChannel <- rmqClient.createConnectionChannel
      rmqHandlers = new RMQMessageHandler[F](orchestrator)
      rmqRouter: Vector[SimexMessageHandler[F]] = RMQMessageRouter.getMessageHandlers(rmqHandlers)

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
    } yield AppService(server, rmqRouter, rmqClient, aMQPChannel)

}
