package simex.server.domain.healthcheck

import cats.Id
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import simex.server.domain.healthcheck.entities.HealthCheckerResponse
import simex.server.domain.healthcheck.entities.HealthStatus.OK

class SelfHealthCheckTest extends AnyFlatSpec {

  val sut = new SelfHealthCheck[Id]

  it should "return OK health status" in {
    val status: Id[HealthCheckerResponse] = sut.checkHealth()

    status.status shouldBe OK
  }

}
