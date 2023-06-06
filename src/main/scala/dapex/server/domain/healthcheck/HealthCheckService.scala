package dapex.server.domain.healthcheck

import cats.data.NonEmptyList
import cats.syntax.all._
import cats.{Monad, Parallel}
import dapex.server.domain.healthcheck.entities.{HealthCheckStatus, HealthStatus}
import org.typelevel.log4cats.Logger

class HealthCheckService[F[_]: Monad: Parallel: Logger](checkers: NonEmptyList[HealthChecker[F]])
    extends HealthCheckAlgebra[F] {

  override def checkHealth: F[HealthCheckStatus] =
    checkers
      .parTraverse(_.checkHealth())
      .flatMap { responses =>
        val statuses = responses.map(_.status).toList
        if (statuses.contains(HealthStatus.BROKEN) || statuses.contains(HealthStatus.BROKEN)) {
          val brokenService = responses.toList.filter(_.status != HealthStatus.OK)
          Logger[F].warn(s"Health Check shows problems: $brokenService") *>
            HealthCheckStatus(HealthStatus.BROKEN, responses).pure[F]
        } else
          HealthCheckStatus(HealthStatus.OK, responses).pure[F]
      }
}

object HealthCheckService {
  def apply[F[_]: Monad: Parallel: Logger](checkers: NonEmptyList[HealthChecker[F]]) =
    new HealthCheckService[F](checkers)
}
