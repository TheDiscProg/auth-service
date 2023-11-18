package simex.authorc.domain.rmqconsumer

import cats.Monad
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import simex.authorc.domain.orchestrator.AuthenticationOrchestratorAlgebra
import simex.messaging.Simex

class RMQMessageHandler[F[_]: Monad: Logger](authOrc: AuthenticationOrchestratorAlgebra[F]) {

  def handleMessageReceived(msg: Simex): F[Unit] =
    for {
      _ <- Logger[F].info(
        s"Message Handler recieved for ${msg.endpoint.resource} ${msg.client.requestId}"
      )
      _ <- authOrc.handleSimexMessage(msg)
    } yield ()

}
