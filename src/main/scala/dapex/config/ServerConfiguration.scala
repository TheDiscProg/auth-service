package dapex.config

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class ServerConfiguration(
    http: HttpConfiguration
)

object ServerConfiguration {
  implicit val serverConfigurationDecoder: Decoder[ServerConfiguration] = deriveDecoder
}
