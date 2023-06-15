package dapex.authorc.domain.orchestrator

import cats.effect._
import dapex.authorc.fixtures.TestObjects
import dapex.messaging.Method.PROCESS
import dapex.messaging.{Criterion, DapexMessage, DapexMessageFixture, Response}
import munit.CatsEffectSuite
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

class AuthenticationOrchestratorTest
    extends CatsEffectSuite
    with Matchers
    with OptionValues
    with DapexMessageFixture {
  import TestObjects._

  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  // Cats-effect 3 IO doesn't handle well mock objects and this is the only way to get the arguments
  val cacheMap = Collections.synchronizedMap(new util.HashMap[String, DapexMessage]())
  val cachingService = new CachingService[IO](cacheMap)
  val queue = new ConcurrentLinkedQueue[DapexMessage]()
  val rmqPublisher = new RMPPublisher[IO](queue)

  val sut = new AuthenticationOrchestrator[IO](
    cachingService,
    rmqPublisher,
    hashingService,
    securityTokenService
  )

  test("handle unsupported method call") {
    cacheMap.clear()
    queue.clear()

    val unsupportedMsg = getMessage(PROCESS)

    val result = sut.handleDapexMessage(unsupportedMsg)

    result.flatMap { r =>
      IO {
        queue.size() shouldBe 0
        r shouldBe ()
      }
    }
  }

  test("handle auth request") {
    cacheMap.clear()
    queue.clear()

    val result = sut.handleDapexMessage(authRequest)

    result.flatMap { _ =>
      IO {
        // It should store the request in cache
        val cachedMsg: Option[Criterion] =
          cacheMap.get(serviceRequestId).criteria.find(_.field.toLowerCase == "password")
        cachedMsg.isDefined shouldBe true
        cachedMsg.value.value shouldBe "hash"

        // It should publish the updated message to the db service
        val msgSentToDb = queue.poll()
        msgSentToDb.endpoint.resource shouldBe "service.dbread"
        msgSentToDb.criteria.find(_.field.toLowerCase == "password").value.value shouldBe "hash"
      }
    }
  }

  test("handle db response when user is found and the password matches") {
    cacheMap.clear()
    queue.clear()

    val originalAuthRequestWithHash = authRequest
      .copy(criteria =
        authRequest.criteria
          .filter(_.field.toLowerCase != "password") :+ Criterion("password", "hash", "EQ")
      )
    cacheMap.put(serviceRequestId, originalAuthRequestWithHash)

    val result = sut.handleDapexMessage(dbResponse)

    result.flatMap { _ =>
      IO {
        // check that it has saved the message with the password hash
        val msgByToken = cacheMap.get("jwt-token")
        msgByToken.response.value.data
          .find(_.field.toLowerCase == "password")
          .value
          .value shouldBe "hash"

        // it should publish the message to collection point
        val msgToCP = queue.poll()
        msgToCP.endpoint.resource shouldBe "service.collection"
        msgToCP.endpoint.method shouldBe "response"
        msgToCP.response.value.status.toLowerCase shouldBe "ok"
        val data = msgToCP.response.value.data
        val securityToken = data.find(_.field.toLowerCase == "securitytoken")
        securityToken.isDefined shouldBe true
        securityToken.value.value shouldBe "jwt-token"
      }
    }

  }

  test("handle db response when password does not match") {
    cacheMap.clear()
    queue.clear()

    val originalAuthRequestWithHash = authRequest
      .copy(criteria =
        authRequest.criteria
          .filter(_.field.toLowerCase != "password") :+ Criterion("password", "differenthash", "EQ")
      )
    cacheMap.put(serviceRequestId, originalAuthRequestWithHash)

    val result = sut.handleDapexMessage(dbResponse)

    result.flatMap { _ =>
      IO {
        // check that it hasn't saved the token
        cacheMap.containsKey("jwt-token") shouldBe false

        // it should publish not found message to collection point
        val msgToCP = queue.poll()
        msgToCP.endpoint.resource shouldBe "service.collection"
        msgToCP.endpoint.method shouldBe "response"
        msgToCP.response.value.status.toLowerCase shouldBe "notfound"
        msgToCP.response.value.message shouldBe "Either the username or the password was not matched"
        msgToCP.response.value.data.size shouldBe 0
      }
    }
  }

  test("handle db response when the user was not found in db") {
    cacheMap.clear()
    queue.clear()

    val originalAuthRequestWithHash = authRequest
      .copy(criteria =
        authRequest.criteria
          .filter(_.field.toLowerCase != "password") :+ Criterion("password", "differenthash", "EQ")
      )
    cacheMap.put(serviceRequestId, originalAuthRequestWithHash)

    val userNotFoundResponse = dbResponse.copy(response =
      Some(Response(status = "Not Found", message = "not found", data = Vector()))
    )

    val result = sut.handleDapexMessage(userNotFoundResponse)

    result.flatMap { _ =>
      IO {
        // check that it hasn't saved the token
        cacheMap.containsKey("jwt-token") shouldBe false

        // it should publish not found message to collection point
        val msgToCP = queue.poll()
        msgToCP.endpoint.resource shouldBe "service.collection"
        msgToCP.endpoint.method shouldBe "response"
        msgToCP.response.value.status.toLowerCase shouldBe "notfound"
        msgToCP.response.value.message shouldBe "Either the username or the password was not matched"
        msgToCP.response.value.data.size shouldBe 0
      }
    }
  }

}
