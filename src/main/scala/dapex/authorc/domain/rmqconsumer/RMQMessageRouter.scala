package dapex.authorc.domain.rmqconsumer

import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.consumer.DapexMessageHandler

object RMQMessageRouter {

  def getMessageHandlers[F[_]](router: RMQMessageHandler[F]): Vector[DapexMessageHandler[F]] =
    Vector(
      DapexMessageHandler(RabbitQueue.SERVICE_AUTHENTICATION_QUEUE, router.handleMessageReceived)
    )

}
