package dapex.server.domain.healthcheck

import cats.Applicative
import dapex.server.domain.healthcheck.entities.{HealthCheckerResponse, HealthStatus}

class SelfHealthCheck[F[_]: Applicative] extends HealthChecker[F] {

  override val name: String = "ServerSelfHealthCheck"

  override def checkHealth(): F[HealthCheckerResponse] =
    Applicative[F].pure(
      HealthCheckerResponse(name, HealthStatus.OK)
    )
}

object SelfHealthCheck {

  def apply[F[_]: Applicative] = new SelfHealthCheck()
}
