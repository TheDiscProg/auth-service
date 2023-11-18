package simex.authorc.fixtures

import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.concurrent._

trait DefaultFutureSetting extends ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(100, Millis))
}
