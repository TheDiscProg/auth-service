package dapex.authorc.domain.caching.hcast

import cats.effect.IO
import dapex.authorc.fixtures.TestObjects.TestImap
import dapex.messaging.DapexMessageFixture
import dapex.messaging.Method.{RESPONSE, SELECT}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class AuthHzcMapsTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with DapexMessageFixture {

  private implicit def unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  import cats.effect.unsafe.implicits.global

  val tokenMap = new TestImap()
  val requestMap = new TestImap()

  val sut = AuthHzcMaps[IO](tokenMap, requestMap, 100L)

  it should "handle token map" in {
    val response = getMessage(RESPONSE)

    val result = (for {
      _ <- sut.saveByToken("token1", response)
      storedValue <- sut.getByToken("token1")
    } yield storedValue).unsafeToFuture()

    whenReady(result) { option =>
      option.isDefined shouldBe true
      option.value shouldBe response
    }
  }

  it should "handle not found key in token map without throwing error" in {
    val result = sut.getByToken("token2").unsafeToFuture()

    whenReady(result) { option =>
      option.isDefined shouldBe false
    }
  }

  it should "handle request map" in {
    val select = getMessage(SELECT)

    val result = (for {
      _ <- sut.saveByRequestId("request1", select)
      storedValue <- sut.getByRequestId("request1")
    } yield storedValue).unsafeToFuture()

    whenReady(result) { option =>
      option.isDefined shouldBe true
      option.value shouldBe select
    }
  }

  it should "handle not found key in request map without throwing error" in {
    val result = sut.getByRequestId("request2").unsafeToFuture()

    whenReady(result) { option =>
      option.isDefined shouldBe false
    }
  }

}
