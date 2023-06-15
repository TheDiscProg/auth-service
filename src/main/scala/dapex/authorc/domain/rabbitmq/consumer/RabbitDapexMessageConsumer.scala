package dapex.authorc.domain.rabbitmq.consumer

import cats.effect.Concurrent
import dapex.authorc.domain.rabbitmq.RabbitQueue
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.AMQPChannel
import org.typelevel.log4cats.Logger
import cats.implicits._
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.config.declaration.{
  DeclarationExchangeConfig,
  DeclarationQueueConfig,
  Durable,
  NonAutoDelete,
  NonExclusive
}

/** A General Purpose Class to:
  * Initialise Rabbit Exhange/Queues with consumers
  * As this is used in different services, it has to create different tpes of queues
  * with options to set up DLX and message TTLs etc.
  */
class RabbitDapexMessageConsumer[F[_]: Concurrent: Logger](rmqClient: RabbitClient[F])(implicit
    channel: AMQPChannel
) {

  val consumers: F[List[DapexMessageConsumer[F]]] =
    for {
      _ <- setUpRabbitMQ
      consumers <- RabbitQueue.values.toList
        .filter(_.consumers)
        .map { queue =>
          for {
            consumer <- addConsumerToQueue(queue)
          } yield consumer
        }
        .sequence
      _ <- Logger[F].info(s"Created RabbitMQ and added consumers. Total Number [${consumers.size}]")
    } yield consumers

  private def addConsumerToQueue(queue: RabbitQueue): F[DapexMessageConsumer[F]] =
    for {
      _ <- Logger[F].info(s"Adding consumer to ${queue.name.value}")
      consumerAcker <- rmqClient.createAckerConsumer[String](queue.name)
    } yield DapexMessageConsumer(queue, consumerAcker)

  private def setUpRabbitMQ(implicit channel: AMQPChannel): F[Unit] =
    for {
      _ <- createExchange
      _ <- createQueues
    } yield ()

  private def createExchange(implicit channel: AMQPChannel): F[Unit] =
    RabbitQueue.values.toVector
      .traverse(queue => createExchangeForQueue(queue)) *>
      ().pure[F]

  private def createExchangeForQueue(queue: RabbitQueue)(implicit channel: AMQPChannel): F[Unit] =
    for {
      _ <- Logger[F].info(s"Setting RMQ Exchange for ${queue.name.value}")
      conf = DeclarationExchangeConfig
        .default(queue.exchange, queue.exchangeType)
        .copy(durable = Durable)
      response <- rmqClient.declareExchange(conf)
    } yield response

  private def createQueues(implicit channel: AMQPChannel): F[Unit] =
    RabbitQueue.values.toVector
      .traverse(queue => createRMQQueue(queue)) *>
      ().pure[F]

  private def createRMQQueue(queue: RabbitQueue)(implicit channel: AMQPChannel): F[Unit] = {
    val arguments = setDLXAndMessageTTLConfigurations(queue.dlx, queue.messageTTL)
    val conf = DeclarationQueueConfig(
      queueName = queue.name,
      durable = Durable,
      exclusive = NonExclusive,
      autoDelete = NonAutoDelete,
      arguments = arguments
    )
    for {
      _ <- Logger[F].info(s"Setting RMQ Queue: ${queue.name.value}")
      _ <- rmqClient.declareQueue(conf)
      response <- rmqClient.bindQueue(
        queueName = queue.name,
        exchangeName = queue.exchange,
        routingKey = queue.routingKey
      )
    } yield response
  }

  private def setDLXAndMessageTTLConfigurations(
      deadLetter: Option[String],
      messageTTL: Option[Long]
  ): Arguments =
    (deadLetter, messageTTL) match {
      case (Some(dlx), Some(ttl)) =>
        Map("x-dead-letter-exchange" -> dlx, "x-message-ttl" -> ttl): Arguments
      case (Some(dlx), None) =>
        Map("x-dead-letter-exchange" -> dlx): Arguments
      case (None, Some(ttl)) =>
        Map("x-message-ttl" -> ttl): Arguments
      case _ => Map.empty: Arguments
    }

}

object RabbitDapexMessageConsumer {
  def apply[F[_]: Concurrent: Logger](
      rabbitClient: RabbitClient[F],
      channel: AMQPChannel
  ): RabbitDapexMessageConsumer[F] = {
    implicit val rmqChannel = channel
    new RabbitDapexMessageConsumer[F](rabbitClient)
  }
}
