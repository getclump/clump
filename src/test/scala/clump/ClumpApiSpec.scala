package clump

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.twitter.util.Future
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class ClumpApiSpec extends Spec {

  "the Clump object" >> {

    "allows to create a constant clump" >> {

      "from a future (Clump.future)" >> {

        "success" in {
          clumpResult(Clump.future(Future.value(Some(1)))) ==== Some(1)
        }

        "failure" in {
          try {
            clumpResult(Clump.future(Future.exception(new IllegalStateException)))
            ko("expected IllegalStateException")
          } catch {
            case e: IllegalStateException => ok
            case _: Throwable => ko("expected IllegalStateException")
          }
        }
      }

      "from a value (Clump.value)" in {
        clumpResult(Clump.value(1)) ==== 1
      }

      "from an option (Clump.value)" >> {

        "defined" in {
          clumpResult(Clump.value(Option(1))) ==== Option(1)
        }

        "empty" in {
          clumpResult(Clump.value(None)) ==== None
        }
      }

      "failed (Clump.exception)" in {
        try {
          clumpResult(Clump.exception(new IllegalStateException))
          ko("expected IllegalStateException")
        } catch {
          case e: IllegalStateException => ok
          case _: Throwable => ko("expected IllegalStateException")
        }
      }
    }

    "allows to create a clump traversing multiple inputs (Clump.traverse)" in {
      val inputs = List(1, 2, 3)
      val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
      clumpResult(clump) ==== List(2, 3, 4)
    }

    "allows to collect multiple clumps in only one (Clump.collect)" in {
      val clumps = List(Clump.value(1), Clump.value(2))
      clumpResult(Clump.collect(clumps)) ==== List(1, 2)
    }

    "allows to create a clump source (Clump.source)" in {
      def fetch(inputs: Set[Int]) =
        Future.value(inputs.map(i => i -> i.toString).toMap)

      val source = Clump.sourceFrom(fetch, 2)

      clumpResult(source(1)) ==== "1"
    }
  }

  "a Clump instance" >> {

    "can be mapped to a new clump" >> {

      "using simple a value transformation (clump.map)" in {
        clumpResult(Clump.value(1).map(_ + 1)) ==== 2
      }

      "using a transformation that creates a new clump (clump.flatMap)" in {
        clumpResult(Clump.value(1).flatMap(i => Clump.value(i + 1))) ==== 2
      }
    }

    "can be joined with another clump and produce a new clump with the value of both (clump.join)" in {
      clumpResult(Clump.value(1).join(Clump.value(2))) ==== (1, 2)
    }

    "allows to recover from failures" >> {

      "using a function that recovers using a new value (clump.handle)" in {
        val clump =
          Clump.exception(new IllegalStateException).handle {
            case e: IllegalStateException => 2
          }
        clumpResult(clump) ==== 2
      }

      "using a function that recovers the failure using a new clump (clump.rescue)" in {
        val clump =
          Clump.exception(new IllegalStateException).rescue {
            case e: IllegalStateException => Clump.value(2)
          }
        clumpResult(clump) ==== 2
      }
    }
  }
}
