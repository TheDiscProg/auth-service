package dapex.server.domain.healthcheck

import cats.Functor
import cats.implicits._
import dapex.guardrail.definitions.HealthResponse
import dapex.guardrail.healthcheck.HealthcheckHandler
import dapex.guardrail.healthcheck.HealthcheckResource.HealthcheckResponse
import dapex.server.domain.healthcheck.entities.HealthCheckStatus
import dapex.server.domain.healthcheck.entities.HealthStatus.OK

class HealthcheckAPIHandler[F[_]: Functor](checker: HealthCheckAlgebra[F])
    extends HealthcheckHandler[F] {
  override def healthcheck(
      respond: HealthcheckResponse.type
  )(): F[HealthcheckResponse] =
    for {
      health <- checker.checkHealth
      response = health match {
        case HealthCheckStatus(OK, _) =>
          HealthcheckResponse.Ok(HealthResponse(HealthResponse.ServiceStatus.Ok))
        case _ => HealthcheckResponse.Ok(HealthResponse(HealthResponse.ServiceStatus.Broken))
      }
    } yield response
}
