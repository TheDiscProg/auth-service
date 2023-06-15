package dapex.authorc.domain.security

import com.google.common.hash.Hashing

import java.nio.charset.StandardCharsets

class HashingService extends HashingServiceAlgebra {

  override def getHash(value: String, salt: String): String =
    Hashing
      .sha256()
      .hashString(s"$value$salt", StandardCharsets.UTF_8)
      .toString
}

object HashingService {
  def apply(): HashingService = new HashingService
}
