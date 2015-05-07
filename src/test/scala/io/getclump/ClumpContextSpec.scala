package io.getclump

import org.junit.runner.RunWith
import com.twitter.util.Await
import com.twitter.util.Future
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpContextSpec extends Spec {

  "returns always the same fetcher for a source" in {
    val context = new ClumpContext
    val source = mock[ClumpSource[Int, Int]]
    context.fetcherFor(source) mustEqual
      context.fetcherFor(source)
  }

  "is stored using a local value" >> {

    "that is propagated to downstream futures" in {
      Await.result {
        Future.value(ClumpContext.default).map {
          _ mustEqual ClumpContext.default
        }
      }
    }

    "that is created for each new execution context" in {
      def context =
        Future.Unit.map { _ =>
          ClumpContext.default
        }
      Await.result(context) mustNotEqual
        Await.result(context)
    }
  }
}
