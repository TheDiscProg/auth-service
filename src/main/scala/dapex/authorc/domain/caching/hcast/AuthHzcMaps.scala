package dapex.authorc.domain.caching.hcast

import cats.Applicative
import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.map.IMap
import dapex.authorc.domain.caching.CachingMapsAlgebra
import dapex.config.HazelcastConfig
import dapex.messaging.DapexMessage
import io.circe.parser._
import io.circe.syntax._
import org.typelevel.log4cats.Logger
import cats.syntax.all._

import java.util.concurrent.TimeUnit

case class AuthHzcMaps[F[_]: Applicative: Logger](
    tokenMap: IMap[String, String],
    authRequestMap: IMap[String, String],
    ttl: Long
) extends CachingMapsAlgebra[F] {

  override def saveByToken(token: String, dapexMessage: DapexMessage): F[Unit] =
    for {
      _ <- Logger[F].info(s"Hzcast Saving Token: $token")
      _ = tokenMap.put(token, dapexMessage.asJson.noSpaces, ttl, TimeUnit.SECONDS)
    } yield ()

  override def getByToken(token: String): F[Option[DapexMessage]] =
    Logger[F].info(s"Hzcast Getting by token: $token") *>
      getDapexMessageFromCache(token, tokenMap)

  override def saveByRequestId(requestId: String, dapexMessage: DapexMessage): F[Unit] =
    for {
      _ <- Logger[F].info(s"Hzcast Saving Auth Request: $requestId")
      _ = authRequestMap.put(requestId, dapexMessage.asJson.noSpaces, ttl, TimeUnit.SECONDS)
    } yield ()

  override def getByRequestId(requestId: String): F[Option[DapexMessage]] =
    Logger[F].info(s"Hzcast Getting auth request: $requestId") *>
      getDapexMessageFromCache(requestId, authRequestMap)

  private def getDapexMessageFromCache(
      key: String,
      map: IMap[String, String]
  ): F[Option[DapexMessage]] =
    if (map.containsKey(key)) {
      val jsonStr = map.get(key)
      decode[DapexMessage](jsonStr) match {
        case Left(e) =>
          Logger[F].warn(s"Hzcast decoding problem: ${e.getMessage}") *>
            (None: Option[DapexMessage]).pure[F]
        case Right(value) => (Some(value): Option[DapexMessage]).pure[F]
      }
    } else {
      Logger[F].warn(s"Hzcast Key [$key] not found in map: ${map.getName}") *>
        (None: Option[DapexMessage]).pure[F]
    }
}

object AuthHzcMaps {

  private val tokenMapName = "auth.token"
  private val authRequest = "auth.request"

  def apply[F[_]: Applicative: Logger](hzConfig: HazelcastConfig): AuthHzcMaps[F] = {
    val clientConfig = getHzcConfiguration(hzConfig)
    configureHzcClient(clientConfig, hzConfig)
  }

  private def configureHzcClient[F[_]: Applicative: Logger](
      clientConfig: ClientConfig,
      config: HazelcastConfig
  ): AuthHzcMaps[F] = {
    val hzClient = HazelcastClient.newHazelcastClient(clientConfig)
    val tokenMap: IMap[String, String] = hzClient.getMap[String, String](tokenMapName)
    val authRequestMap = hzClient.getMap[String, String](authRequest)
    AuthHzcMaps(tokenMap, authRequestMap, config.authTokenTTL)
  }

  private def getHzcConfiguration(config: HazelcastConfig): ClientConfig = {
    val clientConfig = new ClientConfig()
    clientConfig.setClusterName(config.clusterName)
    clientConfig.getNetworkConfig.addAddress(config.clusterAddress)
    clientConfig
  }
}
