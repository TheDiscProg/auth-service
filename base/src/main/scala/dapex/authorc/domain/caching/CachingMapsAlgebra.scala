package dapex.authorc.domain.caching

import dapex.messaging.DapexMessage

trait CachingMapsAlgebra[F[_]] {

  def saveByToken(token: String, dapexMessage: DapexMessage): F[Unit]

  def getByToken(token: String): F[Option[DapexMessage]]

  def saveByRequestId(requestId: String, dapexMessage: DapexMessage): F[Unit]

  def getByRequestId(requestId: String): F[Option[DapexMessage]]
}
