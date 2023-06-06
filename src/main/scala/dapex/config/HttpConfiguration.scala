package dapex.config

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.string.IPv4
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
// Ignore your IDE - below is needed!
import io.circe.refined._

case class HttpConfiguration(port: Int Refined Positive, host: String Refined IPv4)

object HttpConfiguration {
  implicit val httpConfiguration: Decoder[HttpConfiguration] = deriveDecoder
}
