package dapex.authorc.domain.rmqconsumer

import cats.Monad
import cats.syntax.all._
import dapex.authorc.domain.orchestrator.AuthenticationOrchestratorAlgebra
import dapex.messaging.DapexMessage
import org.typelevel.log4cats.Logger

class RMQMessageHandler[F[_]: Monad: Logger](authOrc: AuthenticationOrchestratorAlgebra[F]) {

  def handleMessageReceived(msg: DapexMessage): F[Unit] =
    for {
      _ <- Logger[F].info(
        s"Message Handler recieved for ${msg.endpoint.resource} ${msg.client.requestId}"
      )
      _ <- authOrc.handleDapexMessage(msg)
    } yield ()

}
