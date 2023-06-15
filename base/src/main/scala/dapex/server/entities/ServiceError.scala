package dapex.server.entities

import enumeratum.{Enum, EnumEntry}

sealed trait ServiceError extends EnumEntry {
  val message: String
}

case object ServiceError extends Enum[ServiceError] {

  case class InvalidMessageFormat(message: String) extends ServiceError

  override def values: IndexedSeq[ServiceError] = findValues
}
