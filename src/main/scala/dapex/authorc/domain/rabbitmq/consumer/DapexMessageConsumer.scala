package dapex.authorc.domain.rabbitmq.consumer

import dapex.authorc.domain.rabbitmq.RabbitQueue
import dev.profunktor.fs2rabbit.model.{AckResult, AmqpEnvelope}
import fs2.Stream

case class DapexMessageConsumer[F[_]](
    queue: RabbitQueue,
    ackerAndConsumer: (AckResult => F[Unit], Stream[F, AmqpEnvelope[String]])
)
