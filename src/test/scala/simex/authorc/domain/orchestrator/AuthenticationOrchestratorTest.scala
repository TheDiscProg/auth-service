package simex.authorc.domain.orchestrator

import cats.Monad
import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxApplicativeId
import com.github.blemale.scaffeine.Scaffeine
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import shareprice.config.ServiceDefinition
import shareprice.rabbitmq.SharepriceQueue
import simex.authorc.fixtures.DefaultFutureSetting
import simex.caching.CachingServiceAlgebra
import simex.messaging._
import simex.rabbitmq.RabbitQueue
import simex.rabbitmq.publisher.SimexMQPublisherAlgebra
import simex.test.SimexTestFixture
import thediscprog.utillibrary.hashing.HashingService
import thediscprog.utillibrary.jwt.SecurityTokenService

class AuthenticationOrchestratorTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with BeforeAndAfterEach
    with DefaultFutureSetting
    with SimexTestFixture {

  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private val cache = Scaffeine().build[String, Simex]()
  private val hashingService = HashingService()

  private val sut = getSutForTesting()

  override def beforeEach(): Unit = {
    cache.invalidateAll()
    super.beforeEach()
  }

  it should "handle unsupported method message" in {
    val unsupportedMessage = getMessage(Method.DELETE, Some("Authentication"), Vector())

    val result = sut.handleSimexMessage(unsupportedMessage).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val key =
        s"RMQ-${unsupportedMessage.client.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val sentMessage = cache.getIfPresent(key)
      sentMessage.isDefined shouldBe true
      sentMessage.value.data shouldBe Vector(
        Datum.AuthenticationFailed,
        Datum("message", "AuthOrc: does not support method call: [DELETE]", None)
      )
    }
  }

  it should "handle unsupported entity in message" in {
    val unsupportedMessage = authenticationRequest.copy(
      endpoint = authenticationRequest.endpoint.copy(entity = Some("UserDB")),
      client = authenticationRequest.client.copy(requestId = "badrequest")
    )

    val result = sut.handleSimexMessage(unsupportedMessage).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val key =
        s"RMQ-${unsupportedMessage.client.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val sentMessage = cache.getIfPresent(key)
      sentMessage.isDefined shouldBe true
      sentMessage.value.data shouldBe Vector(
        Datum.AuthenticationFailed,
        Datum("message", "AuthOrc: Unsupported entity: userdb", None)
      )
    }
  }

  it should "send request to database for authentication" in {
    val result = sut.handleSimexMessage(authenticationRequest).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      // Request Message stored in cache with hashed password
      val requestKey =
        s"request-${authenticationRequest.client.clientId}-${authenticationRequest.client.requestId}"
      val request = cache.getIfPresent(requestKey)
      request.isDefined shouldBe true
      request.value.getPassword.value != authenticationRequest.getPassword.value &&
      request.value.getPassword.value.nonEmpty shouldBe true

      // Message sent to db
      val rmqKey =
        s"RMQ-${authenticationRequest.client.requestId}-${SharepriceQueue.SERVICE_DBREAD_QUEUE.name.value}"
      val dbRequest = cache.getIfPresent(rmqKey)
      dbRequest.isDefined shouldBe true
      dbRequest.value.data.head shouldBe authenticationRequest
        .extractDatumByFieldname("username")
        .value
      dbRequest.value.data.size shouldBe 1
    }
  }

  it should "handle token refresh request when refresh token is not presented" in {
    val badRefreshRequest = refreshTokenRequest.copy(data = Vector())
    val result = sut.handleSimexMessage(badRefreshRequest).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val key =
        s"RMQ-${badRefreshRequest.originator.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val messageSentToCP = cache.getIfPresent(key)

      messageSentToCP.isDefined shouldBe true
      messageSentToCP.value.data shouldBe Vector(
        Datum("status", "Authentication failed", None),
        Datum("message", "No refresh token presented in request", None)
      )
    }
  }

  it should "handle token refresh request when the token is not found in cache" in {
    val result = sut.handleSimexMessage(refreshTokenRequest).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val key =
        s"RMQ-${refreshTokenRequest.originator.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val messageSentToCP = cache.getIfPresent(key)

      messageSentToCP.isDefined shouldBe true
      messageSentToCP.value.data shouldBe Vector(
        Datum("status", "Authentication failed", None),
        Datum("message", "No matching refresh token found", None)
      )
    }
  }

  it should "handle token refresh request when token is found in cache" in {
    val refreshDatum =
      refreshTokenRequest.extractDatumByFieldname(Simex.REFRESH_TOKEN).getOrElse(fail())
    val previousTokenKey = s"refresh-${refreshDatum.value}"
    cache.put(previousTokenKey, user)

    val result = sut.handleSimexMessage(refreshTokenRequest).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val erasedToken = cache.getIfPresent(previousTokenKey)
      erasedToken.isDefined shouldBe false

      val newTokenKey =
        s"RMQ-${refreshTokenRequest.originator.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val newTokens = cache.getIfPresent(newTokenKey)
      newTokens.value.extractDatumByFieldname(Simex.AUTHORIZATION).isDefined shouldBe true
      newTokens.value.extractDatumByFieldname(Simex.REFRESH_TOKEN).isDefined shouldBe true

      cache.estimatedSize() shouldBe 3
    }
  }

  it should "handle a not found user in response from user database" in {
    val dbResponse = getMessage(Method.RESPONSE, Some("user"), Vector())

    val result = sut.handleSimexMessage(dbResponse).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()

      // message sent to collection point
      val key =
        s"RMQ-${dbResponse.client.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val response = cache.getIfPresent(key)
      response.isDefined shouldBe true
      response.value.data shouldBe Vector(
        Datum("status", "Authentication failed", None),
        Datum("message", "Either the username or the password did not match", None)
      )
    }
  }

  it should "handle a response when the user is found but password hash does not match" in {
    setUpRequestCache()

    val result = sut.handleSimexMessage(user).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      val key =
        s"RMQ-${user.client.requestId}-${SharepriceQueue.SERVICE_COLLECTION_POINT_QUEUE.name.value}"
      val response = cache.getIfPresent(key)
      response.isDefined shouldBe true
      response.value.data shouldBe Vector(
        Datum("status", "Authentication failed", None),
        Datum("message", "Either the username or the password did not match", None)
      )
    }
  }

  it should "handle a response when the user is authenticated" in {
    setUpRequestCache()
    val password = Datum(
      "password",
      hashingService.generateHash("password1234", "tester@test.com".getBytes, sut.HashingIteration),
      None
    )
    val dbResponse = user.copy(data = user.data.filter(_.field != "password") :+ password)
    println(s"dbRespone: $dbResponse")
    val result = sut.handleSimexMessage(dbResponse).unsafeToFuture()

    whenReady(result) { r =>
      r shouldBe ()
      cache.estimatedSize() shouldBe 4
      val rmqMessageSentKey =
        s"RMQ-${dbResponse.client.requestId}-${ServiceDefinition.CollectionPointService}"
      val messageSentToCollectionPoint = cache.getIfPresent(rmqMessageSentKey)
      messageSentToCollectionPoint.isDefined shouldBe true
      messageSentToCollectionPoint.value.data.size shouldBe 8
    }
  }

  private def getSutForTesting() = {
    val rmqPublisher = new SimexMQPublisherAlgebra[IO] {
      override def publishMessageToQueue(message: Simex, queue: RabbitQueue): IO[Unit] =
        IO {
          cache.put(
            s"RMQ-${message.client.requestId}-${queue.name.value}",
            message
          )
        }
    }

    val securityTokenService = SecurityTokenService("secret")

    class CachingService[F[_]: Monad](prefix: String) extends CachingServiceAlgebra[F] {
      override def saveMessage(key: String, message: Simex): F[Unit] = {
        cache.put(s"$prefix-$key", message)
        ().pure[F]
      }

      override def getMessage(key: String): F[Option[Simex]] =
        cache.getIfPresent(s"$prefix-$key").pure[F]

      override def deleteMessage(key: String): F[Unit] =
        cache.invalidate(s"$prefix-$key").pure[F]
    }

    val requestCachingService = new CachingService[IO]("request")

    val authTokenCachingService = new CachingService[IO]("auth")

    val refreshTokenCachingService = new CachingService[IO]("refresh")

    AuthenticationOrchestrator[IO](
      rmqPublisher,
      hashingService,
      securityTokenService,
      requestCachingService,
      authTokenCachingService,
      refreshTokenCachingService
    )

  }

  def setUpRequestCache() = {
    val username = authenticationRequest.getUsername.value
    val password = authenticationRequest.getPassword.value
    val authRequest = authenticationRequest.replaceDatum(
      Datum(
        "password",
        hashingService.generateHash(password, username.getBytes, sut.HashingIteration),
        None
      )
    )
    val key = s"request-${authRequest.originator.clientId}-${authRequest.originator.requestId}"
    cache.put(key, authRequest)
  }

  private lazy val user = Simex(
    endpoint = Endpoint(
      resource = ServiceDefinition.AuthenticationService,
      method = Method.RESPONSE.value,
      entity = Some("user")
    ),
    client = Client(
      clientId = ServiceDefinition.DatabaseROService,
      requestId = "request1",
      sourceEndpoint = ServiceDefinition.DatabaseROService,
      authorization = "sometoken"
    ),
    originator = Originator(
      clientId = "client1",
      requestId = "request1",
      sourceEndpoint = "app.login",
      originalToken = "originaltoken"
    ),
    data = Vector(
      Datum("userId", "1", None),
      Datum("title", "Mr", None),
      Datum("firstname", "John", None),
      Datum("surname", "Smith", None),
      Datum("username", "tester@test.com", None),
      Datum("password", "password1234", None)
    )
  )

}
