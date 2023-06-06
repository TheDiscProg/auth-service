package dapex.server.domain.healthcheck.entities

sealed trait HealthStatus

object HealthStatus {
  final case object OK extends HealthStatus
  final case object BROKEN extends HealthStatus
  final case class PROBLEM(message: String) extends HealthStatus
}
