package simex.authorc.domain.registration

import simex.messaging.Simex

trait UserRegistrationServiceAlgebra[F[_]] {

  def register(message: Simex): F[Unit]

  def handleDatabaseResponse(message: Simex): F[Unit]

}
