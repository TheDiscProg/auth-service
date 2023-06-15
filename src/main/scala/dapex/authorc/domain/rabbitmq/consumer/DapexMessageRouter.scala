package dapex.authorc.domain.rabbitmq.consumer

import cats.effect.Concurrent
import dapex.authorc.domain.orchestrator.AuthenticationOrchestratorAlgebra
import dapex.authorc.domain.rabbitmq.RabbitQueue
import dapex.messaging.DapexMessage
import dapex.server.entities.ServiceError
import dev.profunktor.fs2rabbit.model
import dev.profunktor.fs2rabbit.model.AckResult.{Ack, NAck}
import dev.profunktor.fs2rabbit.model.{AckResult, DeliveryTag}
import fs2.Stream
import io.circe.Error
import org.typelevel.log4cats.Logger

class DapexMessageRouter[F[_]: Concurrent: Logger](
    consumers: F[List[DapexMessageConsumer[F]]],
    authenticationOrchestrator: AuthenticationOrchestratorAlgebra[F]
) extends DapexMessageRouterAlgebra[F] {

  override val flow: Stream[F, Unit] =
    (
      for {
        c <- Stream.evalSeq(consumers)
        (acker, consumer) = c.ackerAndConsumer
      } yield c.queue match {
        case RabbitQueue.SERVICE_AUTHENTICATION_QUEUE =>
          consumer
            .through(decoder[DapexMessage])
            .flatMap {
              handleDapexMessage(_, c.queue.name)(acker)(
                authenticationOrchestrator.handleDapexMessage
              )
            }
      }
    )
      .parJoin(consumerQueues.size)

  override def handleDapexMessage[E <: ServiceError](
      decodedMsg: (Either[Error, DapexMessage], DeliveryTag),
      queue: model.QueueName
  )(acker: AckResult => F[Unit])(f: DapexMessage => F[Unit]): Stream[F, Unit] =
    decodedMsg match {
      case (Left(error), tag) =>
        Stream
          .eval(Logger[F].warn(error.getMessage))
          .map(_ => NAck(tag))
          .evalMap(acker)
      case (Right(msg), tag) =>
        Stream
          .eval(f(msg))
          .map(_ => Ack(tag))
          .evalMap(acker)
    }
}

object DapexMessageRouter {
  def apply[F[_]: Concurrent: Logger](
      consumers: F[List[DapexMessageConsumer[F]]],
      authenticationOrchestrator: AuthenticationOrchestratorAlgebra[F]
  ): DapexMessageRouter[F] =
    new DapexMessageRouter[F](consumers, authenticationOrchestrator)
}
