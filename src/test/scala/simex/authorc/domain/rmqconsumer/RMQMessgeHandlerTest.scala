package simex.authorc.domain.rmqconsumer

import cats.effect.IO
import munit.CatsEffectSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import simex.authorc.domain.orchestrator.AuthenticationOrchestratorAlgebra
import simex.messaging.Simex
import simex.test.SimexTestFixture

import java.util.concurrent.ConcurrentHashMap

class RMQMessgeHandlerTest
    extends CatsEffectSuite
    with Matchers
    with ScalaFutures
    with SimexTestFixture {
  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private lazy val cacheMap = new ConcurrentHashMap[String, Simex]()

  val orc = new AuthenticationOrchestratorAlgebra[IO] {

    override def handleSimexMessage(message: Simex): IO[Unit] =
      IO {
        cacheMap.put(s"${message.client.requestId}", message)
        ()
      }
  }

  test("it should use the authentication orchestrator to handle messages") {
    val sut = new RMQMessageHandler[IO](orc)
    val message = authenticationRequest
    val response = sut.handleMessageReceived(message).unsafeToFuture()

    whenReady(response) { _ =>
      cacheMap.size() shouldBe 1
      val storedMessage = cacheMap.get(message.client.requestId)
      storedMessage shouldBe message
    }
  }
}
