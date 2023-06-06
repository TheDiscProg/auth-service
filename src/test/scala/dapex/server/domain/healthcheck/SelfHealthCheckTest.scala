package dapex.server.domain.healthcheck

import cats.Id
import dapex.server.domain.healthcheck.entities.{HealthCheckerResponse, HealthStatus}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class SelfHealthCheckTest extends AnyFlatSpec {

  val sut = new SelfHealthCheck[Id]

  it should "return OK health status" in {
    val status: Id[HealthCheckerResponse] = sut.checkHealth()

    status.status shouldBe HealthStatus.OK
  }

}
