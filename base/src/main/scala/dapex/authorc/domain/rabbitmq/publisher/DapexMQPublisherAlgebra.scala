package dapex.authorc.domain.rabbitmq.publisher

import dapex.messaging.DapexMessage

trait DapexMQPublisherAlgebra[F[_]] {

  def publishToCollectionPointQueue(message: DapexMessage): F[Unit]

  def publishToDBReadQueue(message: DapexMessage): F[Unit]
}
