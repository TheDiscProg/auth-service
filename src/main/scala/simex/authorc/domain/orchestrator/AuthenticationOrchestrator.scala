package simex.authorc.domain.orchestrator

import cats.Monad
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import shareprice.config.ServiceDefinition
import shareprice.rabbitmq.SharepriceQueue
import simex.caching.CachingServiceAlgebra
import simex.messaging.{Datum, Endpoint, Method, Simex}
import simex.rabbitmq.publisher.SimexMQPublisherAlgebra
import simex.server.entities.ServiceError.{
  InvalidCredentialsError,
  RefreshTokenError,
  UnsupportedEntityError,
  UnsupportedMethodCallError
}
import simex.server.entities.{ServiceError, UserPrincipal}
import thediscprog.utillibrary.hashing.HashingServiceAlgebra
import thediscprog.utillibrary.jwt.SecurityTokenServiceAlgebra

import java.time.Instant

class AuthenticationOrchestrator[F[_]: Monad: Logger](
    rmqPublisher: SimexMQPublisherAlgebra[F],
    hashingService: HashingServiceAlgebra,
    securityTokenService: SecurityTokenServiceAlgebra,
    requestCachingService: CachingServiceAlgebra[F],
    authTokenCachingService: CachingServiceAlgebra[F],
    refreshTokenCachingService: CachingServiceAlgebra[F]
) extends AuthenticationOrchestratorAlgebra[F] {

  val HashingIteration = 1000
  private val RefreshTokenNotPresentedMessage = "No refresh token presented in request"
  private val RefreshTokenNotFoundMessage = "No matching refresh token found"
  private val UsernameNotFoundMessage = "No matching username found in data"

  override def handleSimexMessage(message: Simex): F[Unit] =
    Method.fromString(message.endpoint.method) match {
      case Method.SELECT => handleAuthenticationOrRefreshTokenMessage(message)
      case Method.RESPONSE => handleDatabaseResponse(message)
      case e =>
        handleUnsupportedCall(
          message,
          UnsupportedMethodCallError(s"AuthOrc: does not support method call: [$e]")
        )
    }

  private def handleDatabaseResponse(message: Simex): F[Unit] =
    message.getUsername.fold(
      handleUnsupportedCall(
        message,
        InvalidCredentialsError(Datum.authenticationFailedMessage.value)
      )
    )(username => handleUserAuthentication(username, message))

  private def handleUserAuthentication(username: String, message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(s"AuthOrc: Authenticating $username")
      requestKey = s"${message.originator.clientId}-${message.originator.requestId}"
      originalRequest <- requestCachingService.getMessage(requestKey)
      _ <- originalRequest.fold(
        Logger[F].warn(
          s"AuthOrc: Original request not found for $username, key[$requestKey]; database response: [$message]"
        )
      )(request =>
        if (request.getPassword == message.getPassword) {
          generateAuthTokens(message, request, false)
        } else {
          handleUnsupportedCall(
            request,
            InvalidCredentialsError(Datum.authenticationFailedMessage.value)
          )
        }
      )
    } yield ()

  private def handleAuthenticationOrRefreshTokenMessage(message: Simex): F[Unit] =
    message.endpoint.entity match {
      case Some(entity) =>
        entity.toLowerCase match {
          case Simex.AUTHENTICATION_ENTITY => handleAuthenticationRequest(message)
          case Simex.REFRESH_TOKEN_ENTITY => handleRefreshTokenRequest(message)
          case e =>
            handleUnsupportedCall(
              message,
              UnsupportedEntityError(s"AuthOrc: Unsupported entity: $e")
            )
        }
      case None =>
        handleUnsupportedCall(message, UnsupportedEntityError(s"AuthOrc: No entity defined"))
    }

  private def handleRefreshTokenRequest(message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(s"AuthOrc: Refresh Token Request")
      datum = message.extractDatumByFieldname(Simex.REFRESH_TOKEN)
      _ <- datum
        .fold(sendRefreshTokenNotFound(message, RefreshTokenNotPresentedMessage))(d =>
          handleRefreshTokenRequest(d, message)
        )
    } yield ()

  private def handleRefreshTokenRequest(refreshToken: Datum, message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(s"AuthOrc: Refreshing token: [${refreshToken.value}]")
      user <- refreshTokenCachingService.getMessage(refreshToken.value)
      _ <- user.fold(sendRefreshTokenNotFound(message, RefreshTokenNotFoundMessage))(simex =>
        for {
          _ <- Logger[F].info(s"AuthOrc: Rotating used refresh token: [${refreshToken.value}]")
          _ <- refreshTokenCachingService.deleteMessage(refreshToken.value)
          _ <- generateAuthTokens(simex, message)
        } yield ()
      )
    } yield ()

  private def generateAuthTokens(
      databaseResponse: Simex,
      request: Simex,
      isRefreshTokenRequest: Boolean = true
  ): F[Unit] = {
    val now = Instant.now()
    val tokens: Option[(String, String)] = databaseResponse.getUsername.map { username =>
      (
        securityTokenService.generateAuthorisationBearerToken(username, now),
        securityTokenService.generateRefreshToken(username)
      )
    }
    tokens match {
      case Some(tokenTupple) =>
        saveAndSendTokens(
          tokenTupple._1,
          tokenTupple._2,
          databaseResponse,
          request,
          isRefreshTokenRequest
        )
      case None => sendRefreshTokenNotFound(request, UsernameNotFoundMessage)
    }
  }

  private def saveAndSendTokens(
      authToken: String,
      refreshToken: String,
      databaseResponse: Simex,
      originalRequest: Simex,
      isRefreshTokenRequest: Boolean = true
  ): F[Unit] =
    for {
      _ <- authTokenCachingService.saveMessage(authToken, databaseResponse)
      _ <- refreshTokenCachingService.saveMessage(refreshToken, databaseResponse)
      tokens = Vector(
        Datum(Simex.AUTHORIZATION, authToken, None),
        Datum(Simex.REFRESH_TOKEN, refreshToken, None)
      )
      messageToSend = originalRequest.copy(
        endpoint = Endpoint(
          resource = ServiceDefinition.CollectionPointService,
          method = Method.RESPONSE.value,
          entity = Some(Simex.REFRESH_TOKEN)
        ),
        data = if (isRefreshTokenRequest) {
          tokens
        } else {
          databaseResponse.data ++ tokens
        }
      )
      _ <- rmqPublisher.publishMessageToQueue(
        messageToSend,
        SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE
      )
    } yield ()
  private def sendRefreshTokenNotFound(message: Simex, error: String): F[Unit] =
    handleUnsupportedCall(message, RefreshTokenError(error))

  private def handleAuthenticationRequest(message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(s"AuthOrc: Received authentication request: $message")
      userPrincipal = UserPrincipal.extractUserPrincipal(message)
      result <- userPrincipal.fold(
        e => handleUnsupportedCall(message, e),
        user => saveAndSendAuthenticationRequest(user, message)
      )
    } yield result

  private def saveAndSendAuthenticationRequest(
      userPrincipal: UserPrincipal,
      message: Simex
  ): F[Unit] =
    for {
      _ <- saveOriginalRequest(userPrincipal, message)
      _ <- sendRequestForUserInformation(message)
    } yield ()

  private def saveOriginalRequest(userPrincipal: UserPrincipal, message: Simex): F[Unit] = {
    val requestKey = s"${message.originator.clientId}-${message.originator.requestId}"
    val hashedPassword = hashingService.generateHash(
      userPrincipal.password,
      userPrincipal.username.getBytes,
      HashingIteration
    )
    val hashedMessge = message.replaceDatum(Datum(Simex.PASSWORD, hashedPassword, None))
    requestCachingService.saveMessage(requestKey, hashedMessge)
  }

  private def sendRequestForUserInformation(message: Simex): F[Unit] = {
    val userInformationRequest = message.copy(
      endpoint = Endpoint(
        ServiceDefinition.DatabaseROService,
        Method.SELECT.value,
        Some("user")
      ),
      client = message.client.copy(
        ServiceDefinition.AuthenticationService
      ),
      data = message.data.filter(!_.field.equalsIgnoreCase("password"))
    )
    rmqPublisher.publishMessageToQueue(userInformationRequest, SharepriceQueue.SERVICE_DBREAD_QUEUE)
  }

  private def handleUnsupportedCall(message: Simex, error: ServiceError): F[Unit] = {
    val data = Vector(Datum.AuthenticationFailed) :+
      (error match {
        case InvalidCredentialsError(_) => Datum.authenticationFailedMessage
        case _ => Datum("message", error.message, None)
      })
    for {
      _ <- Logger[F].warn(s"AuthOrc: Unsupported Message: ${error.message}: [$message]")
      msgToSend = message.copy(
        endpoint = message.endpoint.copy(
          resource = ServiceDefinition.CollectionPointService,
          method = Method.RESPONSE.value
        ),
        client = message.client.copy(
          clientId = ServiceDefinition.AuthenticationService,
          sourceEndpoint = ServiceDefinition.AuthenticationService
        ),
        data = data
      )
      _ <- rmqPublisher.publishMessageToQueue(
        msgToSend,
        SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE
      )
    } yield ()
  }

}

object AuthenticationOrchestrator {
  def apply[F[_]: Monad: Logger](
      rmqPublisher: SimexMQPublisherAlgebra[F],
      hashingService: HashingServiceAlgebra,
      securityTokenService: SecurityTokenServiceAlgebra,
      requestCachingService: CachingServiceAlgebra[F],
      authTokenCachingService: CachingServiceAlgebra[F],
      refreshTokenCachingService: CachingServiceAlgebra[F]
  ): AuthenticationOrchestrator[F] = new AuthenticationOrchestrator[F](
    rmqPublisher,
    hashingService,
    securityTokenService,
    requestCachingService,
    authTokenCachingService,
    refreshTokenCachingService
  )
}
