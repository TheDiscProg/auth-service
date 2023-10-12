package dapex.authorc.domain.security

import dapex.authorc.fixtures.{DefaultFutureSetting, TestObjects}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{LocalDateTime, ZoneOffset}

class JwtSecurityTokenServiceTest extends AnyFlatSpec with Matchers with DefaultFutureSetting {

  private val sut = JwtSecurityTokenService(TestObjects.config)

  private val fixedDate = LocalDateTime.of(2023, 6, 13, 12, 0, 0)
  private val fixedInstant = fixedDate.toInstant(ZoneOffset.UTC)

  it should "generate JWT token" in {
    val result = sut.generateTokenFor("test@test.com", fixedInstant)

    result shouldBe "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpYXQiOjE2ODY2NTc2MDAsInVzZXJuYW1lIjoidGVzdEB0ZXN0LmNvbSIsImxldmVsIjoiYmFzaWMifQ.zmk_8i1_LG6eyqL0eT6gFyuFa_nkMdQdVOMTC8yZ8A8"
  }

}
