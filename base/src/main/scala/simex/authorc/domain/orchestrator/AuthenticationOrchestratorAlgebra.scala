package simex.authorc.domain.orchestrator

import simex.messaging.Simex

trait AuthenticationOrchestratorAlgebra[F[_]] {

  def handleSimexMessage(message: Simex): F[Unit]
}
