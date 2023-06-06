package dapex.server.domain.healthcheck

import dapex.server.domain.healthcheck.entities.HealthCheckStatus

trait HealthCheckAlgebra[F[_]] {

  def checkHealth: F[HealthCheckStatus]
}
