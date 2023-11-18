package simex.server.domain.healthcheck

import cats.Id
import cats.data.NonEmptyList
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import simex.guardrail.definitions.HealthResponse
import simex.guardrail.healthcheck.HealthcheckResource.HealthcheckResponse
import simex.server.domain.healthcheck.entities.HealthStatus.OK
import simex.server.domain.healthcheck.entities.{
  HealthCheckStatus,
  HealthCheckerResponse,
  HealthStatus
}

class HealthcheckAPIHandlerTest extends AnyWordSpec with Matchers with MockitoSugar {

  val checker = mock[HealthCheckAlgebra[Id]]
  val sut = new HealthcheckAPIHandler[Id](checker)

  "When health check endpoint is called, it" should {
    "return ok when there are no issues" in {
      when(checker.checkHealth)
        .thenReturn(
          HealthCheckStatus(
            OK,
            NonEmptyList.of[HealthCheckerResponse](HealthCheckerResponse("self", HealthStatus.OK))
          )
        )

      val result = sut.healthcheck(HealthcheckResponse)()
      result.fold { response =>
        response shouldBe HealthResponse(HealthResponse.ServiceStatus.Ok)
      }

    }

    "return broken when there are issues" in {
      when(checker.checkHealth)
        .thenReturn(
          HealthCheckStatus(
            HealthStatus.BROKEN,
            NonEmptyList.of[HealthCheckerResponse](
              HealthCheckerResponse("self", HealthStatus.BROKEN)
            )
          )
        )

      val result = sut.healthcheck(HealthcheckResponse)()
      result.fold { response =>
        response shouldBe HealthResponse(HealthResponse.ServiceStatus.Broken)
      }
    }
  }
}
