package dapex.server.domain.healthcheck

import dapex.server.domain.healthcheck.entities.HealthCheckerResponse

trait HealthChecker[F[_]] {

  val name: String

  def checkHealth(): F[HealthCheckerResponse]

}
