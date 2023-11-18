package simex.server.domain.healthcheck

import cats.Applicative
import simex.server.domain.healthcheck.entities.HealthCheckerResponse
import simex.server.domain.healthcheck.entities.HealthStatus.OK

class SelfHealthCheck[F[_]: Applicative] extends HealthChecker[F] {

  override val name: String = "ServerSelfHealthCheck"

  override def checkHealth(): F[HealthCheckerResponse] =
    Applicative[F].pure(
      HealthCheckerResponse(name, OK)
    )
}

object SelfHealthCheck {

  def apply[F[_]: Applicative] = new SelfHealthCheck()
}
