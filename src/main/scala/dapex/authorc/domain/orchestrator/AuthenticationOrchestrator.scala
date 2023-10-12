package dapex.authorc.domain.orchestrator
import cats.Monad
import cats.syntax.all._
import dapex.authorc.domain.caching.AuthenticationCachingServiceAlgebra
import dapex.authorc.domain.security.{HashingServiceAlgebra, SecurityTokenServiceAlgebra}
import dapex.messaging._
import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.publisher.DapexMQPublisherAlgebra
import org.typelevel.log4cats.Logger

class AuthenticationOrchestrator[F[_]: Monad: Logger](
    cachingService: AuthenticationCachingServiceAlgebra[F],
    rmqPublisher: DapexMQPublisherAlgebra[F],
    hashingService: HashingServiceAlgebra,
    securityTokenService: SecurityTokenServiceAlgebra
) extends AuthenticationOrchestratorAlgebra[F] {

  override def handleDapexMessage(dapexMessage: DapexMessage): F[Unit] =
    dapexMessage.endpoint.method.toUpperCase match {
      case "SELECT" => handleSelectMessage(dapexMessage)
      case "RESPONSE" => handleResponseMessage(dapexMessage)
      case _ => handleUnsupportedMethodCall(dapexMessage)
    }

  // for now, we only handle requests to authorise the principal
  private def handleSelectMessage(msg: DapexMessage): F[Unit] = {
    val hashedPassword = getPasswordHash(msg)
    val requestId = s"${msg.client.clientId}-${msg.client.requestId}"
    val dbRequest = createDbRequest(msg, requestId, hashedPassword)
    val originalAuthRequest = replacePasswordWithHash(msg, hashedPassword)
    for {
      _ <- Logger[F].info(s"AuthOrc: Request ID: $requestId")
      _ <- Logger[F].info(s"AuthOrc: Sending request to db: $dbRequest")
      _ <- cachingService.saveByRequestId(requestId, originalAuthRequest)
      _ <- rmqPublisher.publishMessageToQueue(dbRequest, RabbitQueue.SERVICE_DBREAD_QUEUE)
    } yield ()
  }

  private def getPasswordHash(msg: DapexMessage): String = {
    val username = msg.criteria.find(_.field.toLowerCase == "username")
    val password = msg.criteria.find(_.field.toLowerCase == "password")
    (username, password) match {
      case (Some(usr), Some(pwd)) => hashingService.getHash(pwd.value, usr.value)
      case _ => ""
    }
  }

  private def createDbRequest(
      msg: DapexMessage,
      requestId: String,
      passwordHash: String
  ): DapexMessage =
    msg.copy(
      endpoint = Endpoint("service.dbread", "select"),
      client = Client(
        clientId = "service.auth",
        requestId = requestId,
        sourceEndpoint = "service.auth",
        authorisation = "shareprice-secret" // move this to conf
      ),
      criteria = msg.criteria
        .filter(_.field.toLowerCase == "username") :+ Criterion("password", passwordHash, "EQ")
    )

  private def replacePasswordWithHash(msg: DapexMessage, passwordHash: String): DapexMessage =
    msg.copy(criteria =
      msg.criteria
        .filter(_.field.toLowerCase != "password") :+ Criterion("password", passwordHash, "EQ")
    )

  private def handleResponseMessage(msg: DapexMessage): F[Unit] = {
    val requestId = msg.client.requestId
    for {
      _ <- Logger[F].info(s"AuthOrc: handling Response from DB for request Id $requestId")
      orgAuthRqs <- cachingService.getByRequestId(requestId)
      _ <- orgAuthRqs match {
        case Some(value) =>
          checkPasswordAndSendResponse(value, msg)
        case None =>
          Logger[F].warn(s"AuthOrc: No msg found in cache for $requestId") *>
            ().pure[F]
      }
    } yield ()
  }

  private def checkPasswordAndSendResponse(
      originalAuthRequest: DapexMessage,
      dbResponse: DapexMessage
  ): F[Unit] = {
    val givenPasswdHash: Option[Criterion] =
      originalAuthRequest.criteria.find(_.field.toLowerCase == "password")
    val savedPasswordHash: Option[FieldValuePair] = dbResponse.response.flatMap { response =>
      response.data.find(_.field.toLowerCase == "password")
    }
    (givenPasswdHash, savedPasswordHash) match {
      case (Some(gph), Some(sph)) =>
        if (gph.value == sph.value)
          cacheAndSendResponseToCollectionPoint(originalAuthRequest, dbResponse)
        else {
          for {
            _ <- Logger[F].info(s"AuthOrc: Original hash: $gph")
            _ <- Logger[F].info(s"AuthOrc: DB Response hash: $sph")
            _ <- sendNotFoundResponseToCollectionPoint(originalAuthRequest, dbResponse)
          } yield ()
        }
      case (h1, h2) =>
        for {
          _ <- Logger[F].info(s"AuthOrc: Original hash: $h1")
          _ <- Logger[F].info(s"AuthOrc: DB Response hash: $h2")
          _ <- sendNotFoundResponseToCollectionPoint(originalAuthRequest, dbResponse)
        } yield ()
    }
  }

  private def handleUnsupportedMethodCall(msg: DapexMessage): F[Unit] =
    Logger[F].warn(s"AuthOrc: Received unsupported message: [$msg]") *> ().pure[F]

  private def sendNotFoundResponseToCollectionPoint(
      originalAuthRequest: DapexMessage,
      dbResponse: DapexMessage
  ): F[Unit] = {
    val response = dbResponse.copy(
      endpoint = Endpoint("service.collection", "response"),
      client = dbResponse.client.copy(clientId = "service.auth"),
      criteria = Vector(),
      response = Some(
        Response(
          status = "NOTFOUND",
          message = "Either the username or the password was not matched",
          data = Vector()
        )
      )
    )
    for {
      _ <- Logger[F].info(
        s"AuthOrc: No matching principal was found: ${originalAuthRequest.criteria.find(_.field == "username")}"
      )
      _ <- rmqPublisher.publishMessageToQueue(response, RabbitQueue.SERVICE_COLLECTION_POINT_QUEUE)
    } yield ()
  }

  private def cacheAndSendResponseToCollectionPoint(
      originalAuthRequest: DapexMessage,
      dbResponse: DapexMessage
  ): F[Unit] = {
    val msgToSaveAndSend = dbResponse.copy(
      criteria = dbResponse.criteria.filter(_.field.toLowerCase != "password")
    )
    val username: Option[String] =
      originalAuthRequest.criteria.find(_.field.toLowerCase == "username").map(_.value)
    username.fold {
      Logger[F].warn(s"AuthOrc: No username found for $originalAuthRequest") *>
        ().pure[F]
    } { user =>
      for {
        token <- securityTokenService.generateTokenFor(user).pure[F]
        _ <- cachingService.saveByToken(token, msgToSaveAndSend)
        response = createAuthenticatedResponse(token, msgToSaveAndSend)
        _ <- rmqPublisher.publishMessageToQueue(
          response,
          RabbitQueue.SERVICE_COLLECTION_POINT_QUEUE
        )
      } yield ()
    }
  }

  private def createAuthenticatedResponse(
      token: String,
      dbResponse: DapexMessage
  ): DapexMessage =
    dbResponse.copy(
      endpoint = Endpoint("service.collection", "response"),
      client = dbResponse.client.copy(clientId = "service.auth"),
      criteria = Vector(),
      response = Some(
        Response(
          status = "OK",
          message = "Authenticated",
          data = replacePasswordWithToken(dbResponse.response, token)
        )
      )
    )

  private def replacePasswordWithToken(
      response: Option[Response],
      token: String
  ): Vector[FieldValuePair] =
    response
      .fold(Vector[FieldValuePair]()) { resp =>
        val data = resp.data.filter(_.field.toLowerCase != "password")
        data :+ FieldValuePair("securityToken", token)
      }
}

object AuthenticationOrchestrator {
  def apply[F[_]: Monad: Logger](
      cachingService: AuthenticationCachingServiceAlgebra[F],
      rmqPublisher: DapexMQPublisherAlgebra[F],
      hashingService: HashingServiceAlgebra,
      securityTokenService: SecurityTokenServiceAlgebra
  ): AuthenticationOrchestrator[F] = new AuthenticationOrchestrator[F](
    cachingService,
    rmqPublisher,
    hashingService,
    securityTokenService
  )
}
