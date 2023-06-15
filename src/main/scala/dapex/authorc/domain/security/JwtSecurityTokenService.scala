package dapex.authorc.domain.security

import dapex.config.ServerConfiguration
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import java.time.Instant

class JwtSecurityTokenService(config: ServerConfiguration) extends SecurityTokenServiceAlgebra {

  private val algorithm: JwtAlgorithm = JwtAlgorithm.HS256

  override def generateTokenFor(username: String, instant: Instant = Instant.now()): String = {
    val principal = Principal(username, "basic")
    val claim = JwtClaim(
      content = principal.asJson.noSpaces,
      issuedAt = Some(instant.getEpochSecond)
    )
    JwtCirce.encode(claim, config.tokenKey, algorithm)
  }

}

object JwtSecurityTokenService {

  def apply(config: ServerConfiguration): SecurityTokenServiceAlgebra = new JwtSecurityTokenService(
    config
  )
}

private case class Principal(
    username: String,
    level: String
)

private object Principal {
  implicit val principalDecoder: Decoder[Principal] = deriveDecoder
  implicit val principalEncoder: Encoder[Principal] = deriveEncoder
}
