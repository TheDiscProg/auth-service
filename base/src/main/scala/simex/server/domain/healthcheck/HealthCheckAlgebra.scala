package simex.server.domain.healthcheck

import simex.server.domain.healthcheck.entities.HealthCheckStatus

trait HealthCheckAlgebra[F[_]] {

  def checkHealth: F[HealthCheckStatus]
}
