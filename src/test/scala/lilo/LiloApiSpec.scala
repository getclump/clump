package lilo

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.twitter.util.Future
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class LiloApiSpec extends Spec {

  "the Lilo object" >> {

    "allows to create a constant lilo" >> {

      "from a value (Lilo.value)" in {
        resultOf(Lilo.value(1)) mustEqual Some(1)
      }

      "from an option (Lilo.value)" >> {

        "defined" in {
          resultOf(Lilo.value(Option(1))) mustEqual Option(1)
        }

        "empty" in {
          resultOf(Lilo.value(None)) mustEqual None
        }
      }

      "failed (Lilo.exception)" in {
        resultOf(Lilo.exception(new IllegalStateException)) must throwA[IllegalStateException]
      }
    }

    "allows to create a lilo traversing multiple inputs (Lilo.traverse)" in {
      val inputs = List(1, 2, 3)
      val lilo = Lilo.traverse(inputs)(i => Lilo.value(i + 1))
      resultOf(lilo) mustEqual Some(List(2, 3, 4))
    }

    "allows to collect multiple lilos in only one (Lilo.collect)" in {
      val lilos = List(Lilo.value(1), Lilo.value(2))
      resultOf(Lilo.collect(lilos)) mustEqual Some(List(1, 2))
    }

    "allows to create a lilo source (Lilo.source)" in {
      def fetch(inputs: List[Int]) =
        Future.value(inputs.map(i => i -> i.toString).toMap)

      val source = Lilo.source(fetch, 2)

      resultOf(source.get(1)) mustEqual Some("1")
    }
  }

  "a Lilo instance" >> {

    "can be mapped to a new lilo" >> {

      "using simple a value transformation (lilo.map)" in {
        resultOf(Lilo.value(1).map(_ + 1)) mustEqual Some(2)
      }

      "using a transformation that creates a new lilo (lilo.flatMap)" in {
        resultOf(Lilo.value(1).flatMap(i => Lilo.value(i + 1))) mustEqual Some(2)
      }
    }

    "can be joined with another lilo and produce a new lilo with the value of both (lilo.join)" in {
      resultOf(Lilo.value(1).join(Lilo.value(2))) mustEqual Some(1, 2)
    }

    "allows to recover from failures" >> {

      "using a function that recovers using a new value (lilo.handle)" in {
        val lilo =
          Lilo.exception(new IllegalStateException).handle {
            case e: IllegalStateException => 2
          }
        resultOf(lilo) mustEqual Some(2)
      }

      "using a function that recovers the failure using a new lilo (lilo.rescue)" in {
        val lilo =
          Lilo.exception(new IllegalStateException).rescue {
            case e: IllegalStateException => Lilo.value(2)
          }
        resultOf(lilo) mustEqual Some(2)
      }
    }

    "can have its result filtered (lilo.withFilter)" in {
      resultOf(Lilo.value(1).withFilter(_ != 1)) mustEqual None
      resultOf(Lilo.value(1).withFilter(_ == 1)) mustEqual Some(1)
    }
  }
}
