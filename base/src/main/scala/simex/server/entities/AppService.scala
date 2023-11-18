package simex.server.entities

import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import org.http4s.server.Server
import simex.rabbitmq.consumer.SimexMessageHandler

case class AppService[F[_]](
    server: Server,
    rmqHandler: Vector[SimexMessageHandler[F]],
    rmqClient: RabbitClient[F],
    channel: AMQPChannel
)
