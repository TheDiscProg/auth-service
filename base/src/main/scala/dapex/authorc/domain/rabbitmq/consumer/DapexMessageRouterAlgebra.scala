package dapex.authorc.domain.rabbitmq.consumer

import dapex.authorc.domain.rabbitmq.RabbitQueue
import dapex.messaging.DapexMessage
import dapex.server.entities.ServiceError
import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder
import dev.profunktor.fs2rabbit.model.{AckResult, AmqpEnvelope, DeliveryTag, QueueName}
import fs2.Stream
import io.circe.{Decoder, Error}

trait DapexMessageRouterAlgebra[F[_]] {

  lazy val fs2JsonDecoder: Fs2JsonDecoder = new Fs2JsonDecoder

  lazy val consumerQueues: Vector[RabbitQueue] = RabbitQueue.values.filter(_.consumers).toVector

  def decoder[A <: DapexMessage: Decoder]
      : Stream[F, AmqpEnvelope[String]] => Stream[F, (Either[Error, A], DeliveryTag)] =
    _.map(fs2JsonDecoder.jsonDecode[A])

  val flow: Stream[F, Unit]

  def handleDapexMessage[E <: ServiceError](
      decodedMsg: (Either[Error, DapexMessage], DeliveryTag),
      queue: QueueName
  )(acker: AckResult => F[Unit])(f: DapexMessage => F[Unit]): Stream[F, Unit]
}
