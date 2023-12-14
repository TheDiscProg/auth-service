package simex.authorc.domain.registration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.blemale.scaffeine.Scaffeine
import fs2.Chunk
import fs2.kafka.{ProducerRecord, ProducerResult}
import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shareprice.config.ServiceDefinition
import shareprice.kafka.SharepriceTopic
import simex.authorc.fixtures.DefaultFutureSetting
import simex.kafka.KafkaTopic
import simex.kafka.producer.SimexKafkaProducer
import simex.messaging.Simex.REGISTRATION_ENTITY
import simex.messaging.{Datum, Method, Simex}
import simex.rabbitmq.RabbitQueue
import simex.rabbitmq.publisher.SimexMQPublisherAlgebra
import simex.test.SimexTestFixture
import thediscprog.utillibrary.hashing.HashingService

class UserRegistrationServiceTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach
    with OptionValues
    with DefaultFutureSetting
    with SimexTestFixture {

  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private val cache = Scaffeine().build[String, Simex]()

  private val incompleteRegistrationRequest = authenticationRequest.copy(
    endpoint = authenticationRequest.endpoint.copy(
      entity = Some(REGISTRATION_ENTITY),
      method = Method.INSERT.value
    )
  )

  private val request = incompleteRegistrationRequest.copy(
    data = Vector(
      Datum("title", "Mr", None),
      Datum("forename", "John", None),
      Datum("surname", "Smith", None),
      Datum("email", "john.smith@test.com", None),
      Datum("username", "johnsmith", None),
      Datum("password", "test1234", None)
    )
  )

  override def beforeEach(): Unit = {
    cache.invalidateAll()
    super.beforeEach()
  }

  private val sut: UserRegistrationService[IO] = getSutForTesting()

  it should "send error message when user data is not supplied" in {
    val result = sut.register(incompleteRegistrationRequest).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val message = cache.getIfPresent("service.collectionPoint").value
      val error = message.extractDatumByFieldname("message")
      error.value.value shouldBe "Insufficient information for user registration"
    }
  }

  it should "send user data through kafka to database for adding user" in {
    val result = sut.register(request).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      println(cache.asMap())
      val message = cache.getIfPresent(SharepriceTopic.DB_WRITE.name)
      message.isDefined shouldBe true
      message.value.getPassword.value == "test1234" shouldBe false
    }
  }

  it should "send db response to collection point service" in {
    val dbResponse = request.copy(
      endpoint = request.endpoint.copy(
        resource = ServiceDefinition.AuthenticationService,
        method = Method.RESPONSE.value
      ),
      client = request.client.copy(
        clientId = ServiceDefinition.DatabaseUpdateService
      ),
      data = request.data ++ Vector(
        Datum("customerId", "1", None),
        Datum("userId", "1", None),
        Datum.OkayStatus,
        Datum("message", "user created", None)
      )
    )

    val result = sut.handleDatabaseResponse(dbResponse).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      println(cache.asMap())
      val message = cache.getIfPresent("service.collectionPoint")
      message.isDefined shouldBe true
    }
  }

  private def getSutForTesting() = {

    val rmqPub = new SimexMQPublisherAlgebra[IO] {
      override def publishMessageToQueue(message: Simex, queue: RabbitQueue): IO[Unit] =
        IO {
          cache.put(queue.name.value, message)
        }
    }

    val kafkaProducer = new SimexKafkaProducer[IO] {

      override def publishMessge(
          msg: Simex,
          topic: KafkaTopic
      ): IO[ProducerResult[String, String]] =
        IO {
          cache.put(topic.name, msg)
          new Chunk[(ProducerRecord[String, String], RecordMetadata)] {
            override def size: Int = 1

            override def apply(i: Int): (ProducerRecord[String, String], RecordMetadata) = ???

            override def copyToArray[O2 >: (ProducerRecord[String, String], RecordMetadata)](
                xs: Array[O2],
                start: Int
            ): Unit = ()

            override protected def splitAtChunk_(n: Int): (
                Chunk[(ProducerRecord[String, String], RecordMetadata)],
                Chunk[(ProducerRecord[String, String], RecordMetadata)]
            ) = ???
          }
        }
    }

    val hashingService = HashingService()

    UserRegistrationService[IO](rmqPub, kafkaProducer, hashingService)
  }

}
