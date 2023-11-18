package simex.server.domain.healthcheck.entities

case class HealthCheckerResponse(
    name: String,
    status: HealthStatus
)
