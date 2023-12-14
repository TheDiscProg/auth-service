package simex.authorc.domain.registration
import cats.Monad
import org.typelevel.log4cats.Logger
import simex.kafka.producer.SimexKafkaProducer
import simex.messaging.{Datum, Method, Simex}
import simex.server.entities.{ServiceError, UserPrincipal}
import simex.server.entities.ServiceError.InsufficientData
import cats.syntax.all._
import shareprice.config.ServiceDefinition
import shareprice.kafka.SharepriceTopic
import shareprice.rabbitmq.SharepriceQueue
import simex.authorc.domain.HashingIteration
import simex.rabbitmq.publisher.SimexMQPublisherAlgebra
import thediscprog.utillibrary.hashing.HashingServiceAlgebra

class UserRegistrationService[F[_]: Monad: Logger](
    rmqPublisher: SimexMQPublisherAlgebra[F],
    kafkaProcucer: SimexKafkaProducer[F],
    hashingService: HashingServiceAlgebra
) extends UserRegistrationServiceAlgebra[F] {

  private val minimumRestirationFields =
    Vector("title", "forename", "surname", "email", "username", "password")

  override def register(message: Simex): F[Unit] =
    if (checkUserData(message.data)) {
      handleRegistrationMessage(message)
    } else {
      handleUnsupportedCall(
        message,
        InsufficientData("Insufficient information for user registration")
      )
    }

  override def handleDatabaseResponse(message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(
        s"UserRegistration: Received database response - ${message.client.requestId}"
      )
      msg = message.copy(
        endpoint = message.endpoint.copy(resource = ServiceDefinition.CollectionPointService),
        client = message.client.copy(
          clientId = ServiceDefinition.AuthenticationService,
          sourceEndpoint = ServiceDefinition.AuthenticationService
        )
      )
      _ <- rmqPublisher.publishMessageToQueue(msg, SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE)
    } yield ()

  private def checkUserData(data: Vector[Datum]): Boolean = {
    val fieldsIncluded = minimumRestirationFields.map(field => data.map(_.field).contains(field))
    val nonEmpty = data.map(_.value).map(str => str.nonEmpty)

    (fieldsIncluded ++ nonEmpty).toSet == Set(true)
  }

  private def handleRegistrationMessage(message: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(s"UserRegistration: Registering user")
      userPrincipal = UserPrincipal.extractUserPrincipal(message)
      _ <- userPrincipal.fold(
        e => handleUnsupportedCall(message, e),
        user => sendUserRegistration(user, message)
      )
    } yield ()

  private def sendUserRegistration(user: UserPrincipal, message: Simex): F[Unit] = {
    val hashedPassword =
      hashingService.generateHash(user.password, user.username.getBytes, HashingIteration)
    val messageForDb = message.copy(
      endpoint = message.endpoint.copy(
        resource = ServiceDefinition.DatabaseUpdateService
      ),
      client = message.client.copy(
        clientId = ServiceDefinition.AuthenticationService,
        requestId = s"${message.originator.clientId}-${message.originator.requestId}"
      )
    )
    val hashedMessageForDB = messageForDb.replaceDatum(Datum(Simex.PASSWORD, hashedPassword, None))
    kafkaProcucer.publishMessge(hashedMessageForDB, SharepriceTopic.DB_WRITE).map(_ => ())
  }

  private def handleUnsupportedCall(message: Simex, error: ServiceError): F[Unit] =
    for {
      _ <- Logger[F].info(s"UserRegistration: ${error.message} for $message")
      msgToSend = message.copy(
        endpoint = message.endpoint.copy(
          resource = ServiceDefinition.CollectionPointService,
          method = Method.RESPONSE.value
        ),
        client = message.client.copy(
          clientId = ServiceDefinition.AuthenticationService,
          sourceEndpoint = ServiceDefinition.AuthenticationService
        ),
        data = message.data :+ Datum("message", error.message, None)
      )
      _ <- rmqPublisher.publishMessageToQueue(
        msgToSend,
        SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE
      )
    } yield ()
}

object UserRegistrationService {

  def apply[F[_]: Monad: Logger](
      rmqPublisher: SimexMQPublisherAlgebra[F],
      kafkaProcucer: SimexKafkaProducer[F],
      hashingService: HashingServiceAlgebra
  ) = new UserRegistrationService[F](rmqPublisher, kafkaProcucer, hashingService)
}
