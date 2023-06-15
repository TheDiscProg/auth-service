package dapex.config

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class HazelcastConfig(
    clusterName: String,
    clusterAddress: String,
    ports: String,
    outwardPort: String,
    authTokenTTL: Long
)

object HazelcastConfig {
  implicit val hzcConfigurationDecoder: Decoder[HazelcastConfig] = deriveDecoder
}
