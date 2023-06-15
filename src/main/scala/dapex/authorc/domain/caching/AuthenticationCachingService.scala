package dapex.authorc.domain.caching
import dapex.messaging.DapexMessage

class AuthenticationCachingService[F[_]](cache: CachingMapsAlgebra[F])
    extends AuthenticationCachingServiceAlgebra[F] {

  override def saveByToken(token: String, dapexMessage: DapexMessage): F[Unit] =
    cache.saveByToken(token, dapexMessage)

  override def getByToken(token: String): F[Option[DapexMessage]] =
    cache.getByToken(token)

  override def saveByRequestId(requestId: String, dapexMessage: DapexMessage): F[Unit] =
    cache.saveByRequestId(requestId, dapexMessage)

  override def getByRequestId(requestId: String): F[Option[DapexMessage]] =
    cache.getByRequestId(requestId)
}

object AuthenticationCachingService {
  def apply[F[_]](
      cache: CachingMapsAlgebra[F]
  ): AuthenticationCachingService[F] = new AuthenticationCachingService[F](cache)
}
