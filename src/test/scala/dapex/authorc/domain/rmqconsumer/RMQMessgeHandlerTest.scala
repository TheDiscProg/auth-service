package dapex.authorc.domain.rmqconsumer

import cats.effect.IO
import dapex.authorc.domain.orchestrator.AuthenticationOrchestratorAlgebra
import dapex.messaging.DapexMessage
import dapex.messaging.Method.SELECT
import dapex.test.DapexMessageFixture
import munit.CatsEffectSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.ConcurrentHashMap

class RMQMessgeHandlerTest
    extends CatsEffectSuite
    with Matchers
    with ScalaFutures
    with DapexMessageFixture {
  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private lazy val cacheMap = new ConcurrentHashMap[String, DapexMessage]()

  val orc = new AuthenticationOrchestratorAlgebra[IO] {

    override def handleDapexMessage(dapexMessage: DapexMessage): IO[Unit] =
      IO {
        cacheMap.put(s"${dapexMessage.client.requestId}", dapexMessage)
        ()
      }
  }

  test("it should use the authentication orchestrator to handle messages") {
    val sut = new RMQMessageHandler[IO](orc)
    val message = getMessage(SELECT)
    val response = sut.handleMessageReceived(message).unsafeToFuture()

    whenReady(response) { _ =>
      cacheMap.size() shouldBe 1
      val storedMessage: DapexMessage = cacheMap.get(message.client.requestId)
      storedMessage shouldBe message
    }
  }
}
