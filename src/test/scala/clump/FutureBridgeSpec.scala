package clump

import scala.concurrent.{ Await => ScalaAwait, Future => ScalaFuture }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.junit.runner.RunWith
import com.twitter.util.{Future => TwitterFuture, Await => TwitterAwait}
import org.specs2.runner.JUnitRunner
import FutureBridge._

@RunWith(classOf[JUnitRunner])
class FutureBridgeSpec extends Spec {

  "success" >> {

    "twitterToScala" in {
      val twitter = TwitterFuture.value(1)
      val scala: ScalaFuture[Int] = twitter
      ScalaAwait.result(scala, Duration.Inf) mustEqual 1
    }

    "scalaToTwitter" in {
      val scala = ScalaFuture(1)
      val twitter: TwitterFuture[Int] = scala
      TwitterAwait.result(twitter) mustEqual 1
    }
  }

  "failure" >> {

    "twitterToScala" in {
      val twitter = TwitterFuture.exception(new IllegalStateException)
      val scala: ScalaFuture[Int] = twitter
      ScalaAwait.result(scala, Duration.Inf) must throwA[IllegalStateException]
    }

    "scalaToTwitter" in {
      val scala = ScalaFuture.failed(new IllegalStateException)
      val twitter: TwitterFuture[Int] = scala
      TwitterAwait.result(twitter) must throwA[IllegalStateException]
    }
  }
}