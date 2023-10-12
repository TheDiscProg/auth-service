package dapex.server.entities

import dapex.rabbitmq.consumer.DapexMessageHandler
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import org.http4s.server.Server

case class AppService[F[_]](
    server: Server,
    rmqHandler: Vector[DapexMessageHandler[F]],
    rmqClient: RabbitClient[F],
    channel: AMQPChannel
)
