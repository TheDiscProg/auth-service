package dapex.authorc.domain.security

trait HashingServiceAlgebra {

  def getHash(value: String, salt: String): String
}
