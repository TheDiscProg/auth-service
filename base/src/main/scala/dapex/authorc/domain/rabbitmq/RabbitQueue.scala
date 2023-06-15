package dapex.authorc.domain.rabbitmq

import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import enumeratum._

import scala.collection.immutable

sealed trait RabbitQueue extends EnumEntry {
  val name: QueueName
  val routingKey: RoutingKey
  val exchange: ExchangeName
  val dlx: Option[String]
  val exchangeType: ExchangeType
  val messageTTL: Option[Long]
  val consumers: Boolean
}

case object RabbitQueue extends Enum[RabbitQueue] {

  case object SERVICE_AUTHENTICATION_QUEUE extends RabbitQueue {
    override val name: QueueName = QueueName("service.auth")
    override val routingKey: RoutingKey = RoutingKey("service.auth")
    override val exchange: ExchangeName = ExchangeName("shareprice")
    override val dlx: Option[String] = Some("dlg.shareprice")
    override val exchangeType: ExchangeType = ExchangeType.Topic
    override val messageTTL: Option[Long] = None
    override val consumers: Boolean = true
  }

  case object SERVICE_DBREAD_QUEUE extends RabbitQueue {
    override val name: QueueName = QueueName("service.dbread")
    override val routingKey: RoutingKey = RoutingKey("service.dbread")
    override val exchange: ExchangeName = ExchangeName("shareprice")
    override val dlx: Option[String] = Some("dlq.shareprice")
    override val exchangeType: ExchangeType = ExchangeType.Topic
    override val messageTTL: Option[Long] = None
    override val consumers: Boolean = false
  }

  case object SERVICE_COLLECTION_POINT_QUEUE extends RabbitQueue {
    override val name: QueueName = QueueName("service.collectionPoint")
    override val routingKey: RoutingKey = RoutingKey("service.collectionPoint")
    override val exchange: ExchangeName = ExchangeName("shareprice")
    override val dlx: Option[String] = Some("dlq.shareprice")
    override val exchangeType: ExchangeType = ExchangeType.Topic
    override val messageTTL: Option[Long] = None
    override val consumers: Boolean = false
  }

  override def values: immutable.IndexedSeq[RabbitQueue] = findValues
}
