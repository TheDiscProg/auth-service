package dapex.authorc.domain.rabbitmq.publisher

import cats.Applicative
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import dapex.authorc.domain.rabbitmq.RabbitQueue
import dapex.authorc.domain.rabbitmq.RabbitQueue.{
  SERVICE_COLLECTION_POINT_QUEUE,
  SERVICE_DBREAD_QUEUE
}
import dapex.messaging.DapexMessage
import dev.profunktor.fs2rabbit.effects.MessageEncoder
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder
import dev.profunktor.fs2rabbit.model.AmqpMessage
import io.circe.Encoder
import org.typelevel.log4cats.Logger

class DapexMQPublisher[F[_]: Sync: Logger](rabbitClient: RabbitClient[F])
    extends DapexMQPublisherAlgebra[F] {
  import DapexMQPublisher._

  override def publishToCollectionPointQueue(message: DapexMessage): F[Unit] =
    for {
      _ <- Logger[F].info(s"Publishing message to Collection Point: $message")
      response <- publishMessageToQueue(message, SERVICE_COLLECTION_POINT_QUEUE)
    } yield response

  override def publishToDBReadQueue(message: DapexMessage): F[Unit] =
    for {
      _ <- Logger[F].info(s"Publishing message to Collection Point: $message")
      response <- publishMessageToQueue(message, SERVICE_DBREAD_QUEUE)
    } yield response

  private def publishMessageToQueue(message: DapexMessage, queue: RabbitQueue): F[Unit] =
    for {
      response <- rabbitClient.createConnectionChannel
        .use { implicit channel =>
          rabbitClient
            .createPublisher(
              queue.exchange,
              queue.routingKey
            )(channel, encoder[F, DapexMessage])
            .flatMap { f =>
              f(message)
            }
        }
    } yield response
}

object DapexMQPublisher {
  object ioEncoder extends Fs2JsonEncoder

  implicit def encoder[F[_]: Applicative, A](implicit enc: Encoder[A]): MessageEncoder[F, A] =
    Kleisli { (a: A) =>
      val message = enc(a).noSpaces
      AmqpMessage.stringEncoder[F].run(message)
    }
}
