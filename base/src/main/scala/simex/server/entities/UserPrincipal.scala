package simex.server.entities

import simex.messaging.Simex
import simex.server.entities.ServiceError.InvalidCredentialsError

case class UserPrincipal(username: String, password: String)

object UserPrincipal {

  def extractUserPrincipal(message: Simex): Either[ServiceError, UserPrincipal] =
    (message.getUsername, message.getPassword) match {
      case (Some(username), Some(password)) => Right(UserPrincipal(username, password))
      case (_, _) => Left(InvalidCredentialsError("Either username or password is missing"))
    }
}
