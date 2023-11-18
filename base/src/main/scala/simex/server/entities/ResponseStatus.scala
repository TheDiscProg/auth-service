package simex.server.entities

import simex.messaging.Datum

object ResponseStatus {

  val NotFound = Datum("status", "Not Found", None)
  val NoMatchingCredentials =
    Datum("message", "Either the username or the password did not match", None)
}
