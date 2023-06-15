package dapex.authorc.domain.caching

import cats.effect.IO
import dapex.authorc.fixtures.TestObjects.CachingMaps
import dapex.messaging.Method.{RESPONSE, SELECT}
import dapex.messaging.{DapexMessage, DapexMessageFixture}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentHashMap

class AuthenticationCachingServiceTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with DapexMessageFixture {

  import cats.effect.unsafe.implicits.global

  val cacheMap = new ConcurrentHashMap[String, DapexMessage]()
  val cacheMaps = new CachingMaps(cacheMap)

  val sut = AuthenticationCachingService[IO](cacheMaps)

  it should "save and retrieve by token" in {
    val msg = getMessage(RESPONSE)

    val result = for {
      _ <- sut.saveByToken("token1", msg)
      storedValue <- sut.getByToken("token1")
    } yield storedValue

    whenReady(result.unsafeToFuture()) { option =>
      option.isDefined shouldBe true
      option.value shouldBe msg
    }
  }

  it should "save and retrieve by request id" in {
    val msg = getMessage(SELECT)

    val result = for {
      _ <- sut.saveByRequestId("request1", msg)
      storedValue <- sut.getByRequestId("request1")
    } yield storedValue

    whenReady(result.unsafeToFuture()) { option =>
      option.isDefined shouldBe true
      option.value shouldBe msg
    }
  }

}
