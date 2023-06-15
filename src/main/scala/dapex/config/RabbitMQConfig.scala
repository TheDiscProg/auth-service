package dapex.config

import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.refined._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class RabbitMQConfig(
    exchangeName: String Refined NonEmpty,
    host: String Refined NonEmpty,
    port: Int Refined Positive,
    username: String Refined NonEmpty,
    password: String Refined NonEmpty,
    ssl: Option[RabbitMQSSLConfig]
) {
  def asFs2RabbitConfig: Fs2RabbitConfig =
    Fs2RabbitConfig(
      host = host.value,
      port = port.value,
      virtualHost = "/",
      connectionTimeout = FiniteDuration(60, TimeUnit.SECONDS),
      ssl = ssl.isDefined,
      username = Some(username.value),
      password = Some(password.value),
      requeueOnNack = false,
      requeueOnReject = false,
      internalQueueSize = Some(1000)
    )
}

object RabbitMQConfig {

  implicit val rabbitMQConfigDecoder: Decoder[RabbitMQConfig] = deriveDecoder
}
