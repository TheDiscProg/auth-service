package simex.authorc.domain.rmqconsumer

import shareprice.rabbitmq.SharepriceQueue
import simex.rabbitmq.consumer.SimexMessageHandler

object RMQMessageRouter {

  def getMessageHandlers[F[_]](router: RMQMessageHandler[F]): Vector[SimexMessageHandler[F]] =
    Vector(
      SimexMessageHandler(
        SharepriceQueue.SERVICE_AUTHENTICATION_QUEUE,
        router.handleMessageReceived
      )
    )

}
