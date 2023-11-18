package simex.server.domain.healthcheck.entities

import cats.data.NonEmptyList

case class HealthCheckStatus(
    status: HealthStatus,
    details: NonEmptyList[HealthCheckerResponse]
)
