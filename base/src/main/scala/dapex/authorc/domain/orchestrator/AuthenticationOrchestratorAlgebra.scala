package dapex.authorc.domain.orchestrator

import dapex.messaging.DapexMessage

trait AuthenticationOrchestratorAlgebra[F[_]] {

  def handleDapexMessage(dapexMessage: DapexMessage): F[Unit]
}
