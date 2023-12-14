package simex.server.entities

import enumeratum.{Enum, EnumEntry}

sealed trait ServiceError extends EnumEntry {
  val message: String
}

case object ServiceError extends Enum[ServiceError] {

  case class InvalidMessageFormat(message: String) extends ServiceError

  case class UnsupportedEntityError(message: String) extends ServiceError

  case class UnsupportedMethodCallError(message: String) extends ServiceError

  case class InvalidCredentialsError(message: String) extends ServiceError

  case class RefreshTokenError(message: String) extends ServiceError

  case class InsufficientData(message: String) extends ServiceError

  override def values: IndexedSeq[ServiceError] = findValues
}
