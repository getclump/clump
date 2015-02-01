package clump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.twitter.util.{ Future, Await }

@RunWith(classOf[JUnitRunner])
class CumpContextSpec extends Spec {

  "returns always the same fetcher for a source" in {
    val context = new ClumpContext
    val source = mock[ClumpSource[Int, Int]]
    context.fetcherFor(source) mustEqual
      context.fetcherFor(source)
  }

  "is stored using a local value" >> {

    "that is propagated to downstream futures" in {
      Await.result {
        Future.value(ClumpContext()).map {
          _ mustEqual ClumpContext()
        }
      }
    }

    "that is created for each new execution context" in {
      def context =
        Future.Unit.map { _ =>
          ClumpContext()
        }
      Await.result(context) mustNotEqual
        Await.result(context)
    }
  }
}
