package dapex.config

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class RabbitMQSSLConfig(
    keyPassPhrase: String,
    trustPassPhrase: String,
    keyCertPath: String,
    trustStorePath: String
)

object RabbitMQSSLConfig {
  implicit val rabbitSslConfig: Decoder[RabbitMQSSLConfig] = deriveDecoder
}
