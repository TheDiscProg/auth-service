package dapex.authorc.domain.security

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HashingServiceTest extends AnyFlatSpec with Matchers {

  val sut = HashingService()

  it should "return SHA-256 hash" in {
    val hash = sut.getHash("password1234", "test@test.com")

    hash shouldBe "292a57495b6109f59f40e1bf470c205dbb2efd8eb73d49777c429454207dd9df"
  }
}
