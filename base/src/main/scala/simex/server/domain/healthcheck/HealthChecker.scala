package simex.server.domain.healthcheck

import simex.server.domain.healthcheck.entities.HealthCheckerResponse

trait HealthChecker[F[_]] {

  val name: String

  def checkHealth(): F[HealthCheckerResponse]

}
