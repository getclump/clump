package clump

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.{ Await, Future }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class ClumpApiSpec extends Spec {

  "the Clump object" >> {

    "allows to create a constant clump" >> {

      "from a future (Clump.future)" >> {

        "success" in {
          clumpResult(Clump.future(Future.value(Some(1)))) mustEqual Some(1)
        }

        "failure" in {
          clumpResult(Clump.future(Future.exception(new IllegalStateException))) must throwA[IllegalStateException]
        }
      }

      "from a value (Clump.value)" in {
        clumpResult(Clump.value(1)) mustEqual Some(1)
      }

      "from an option (Clump.value)" >> {

        "defined" in {
          clumpResult(Clump.value(Option(1))) mustEqual Option(1)
        }

        "empty" in {
          clumpResult(Clump.value(None)) mustEqual None
        }
      }

      "failed (Clump.exception)" in {
        clumpResult(Clump.exception(new IllegalStateException)) must throwA[IllegalStateException]
      }
    }

    "allows to create a clump traversing multiple inputs (Clump.traverse)" in {
      val inputs = List(1, 2, 3)
      val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
      clumpResult(clump) mustEqual Some(List(2, 3, 4))
    }

    "allows to collect multiple clumps in only one (Clump.collect)" in {
      val clumps = List(Clump.value(1), Clump.value(2))
      clumpResult(Clump.collect(clumps)) mustEqual Some(List(1, 2))
    }

    "allows to create an empty Clump (Clump.empty)" in {
      clumpResult(Clump.empty) ==== None
    }
  }

  "a Clump instance" >> {

    "can be mapped to a new clump" >> {

      "using simple a value transformation (clump.map)" in {
        clumpResult(Clump.value(1).map(_ + 1)) mustEqual Some(2)
      }

      "using a transformation that creates a new clump (clump.flatMap)" >> {
        "both clumps are defined" in {
          clumpResult(Clump.value(1).flatMap(i => Clump.value(i + 1))) mustEqual Some(2)
        }
        "initial clump is undefined" in {
          clumpResult(Clump.value(None).flatMap(i => Clump.value(2))) mustEqual None
        }
      }
    }

    "can be joined with another clump and produce a new clump with the value of both (clump.join)" >> {
      "both clumps are defined" in {
        clumpResult(Clump.value(1).join(Clump.value(2))) mustEqual Some(1, 2)
      }
      "one of them is undefined" in {
        clumpResult(Clump.value(1).join(Clump.value(None))) mustEqual None
      }
    }

    "allows to recover from failures" >> {

      "using a function that recovers using a new value (clump.handle)" >> {
        "exception happens" in {
          val clump =
            Clump.exception(new IllegalStateException).handle {
              case e: IllegalStateException => Some(2)
            }
          clumpResult(clump) mustEqual Some(2)
        }
        "exception doesn't happen" in {
          val clump =
            Clump.value(1).handle {
              case e: IllegalStateException => None
            }
          clumpResult(clump) mustEqual Some(1)
        }
        "exception isn't caught" in {
          val clump =
            Clump.exception(new NullPointerException).handle {
              case e: IllegalStateException => Some(1)
            }
          clumpResult(clump) must throwA[NullPointerException]
        }
      }

      "using a function that recovers the failure using a new clump (clump.rescue)" >> {
        "exception happens" in {
          val clump =
            Clump.exception(new IllegalStateException).rescue {
              case e: IllegalStateException => Clump.value(2)
            }
          clumpResult(clump) mustEqual Some(2)
        }
        "exception doesn't happen" in {
          val clump =
            Clump.value(1).rescue {
              case e: IllegalStateException => Clump.value(None)
            }
          clumpResult(clump) mustEqual Some(1)
        }
        "exception isn't caught" in {
          val clump =
            Clump.exception(new NullPointerException).rescue {
              case e: IllegalStateException => Clump.value(1)
            }
          clumpResult(clump) must throwA[NullPointerException]
        }
      }
    }

    "can have its result filtered (clump.filter)" in {
      clumpResult(Clump.value(1).filter(_ != 1)) mustEqual None
      clumpResult(Clump.value(1).filter(_ == 1)) mustEqual Some(1)
    }

    "uses a covariant type parameter" in {
      trait A
      class B extends A
      class C extends A
      val clump = Clump.traverse(List(new B, new C))(Clump.value(_))
      (clump: Clump[List[A]]) must beAnInstanceOf[Clump[List[A]]]
    }

    "allows to defined a fallback value (clump.orElse)" >> {
      "undefined" in {
        clumpResult(Clump.empty.orElse(Clump.value(1))) ==== Some(1)
      }
      "defined" in {
        clumpResult(Clump.value(Some(1)).orElse(Clump.value(2))) ==== Some(1)
      }
    }

    "can represent its result as a list (clump.list) when its type is List[T]" in {
      Await.result(Clump.value(List(1, 2)).list) ==== List(1, 2)
      // Clump.value(1).list doesn't compile
    }

    "can provide a result falling back to a default (clump.getOrElse)" >> {
      "initial clump is undefined" in {
        Await.result(Clump.value(None).getOrElse(1)) ==== 1
      }

      "initial clump is defined" in {
        Await.result(Clump.value(Some(2)).getOrElse(1)) ==== 2
      }
    }

    "has a utility method (clump.apply) for unwrapping optional result" in {
      Await.result(Clump.value(1).apply()) ==== 1
      Await.result(Clump.value[Int](None)()) must throwA[NoSuchElementException]
    }

    "can be made optional (clump.optional) to avoid lossy joins" in {
      val clump: Clump[String] = Clump.empty
      val optionalClump: Clump[Option[String]] = clump.optional
      clumpResult(optionalClump) ==== Some(None)

      val valueClump: Clump[String] = Clump.value("foo")
      clumpResult(valueClump.join(clump)) ==== None
      clumpResult(valueClump.join(optionalClump)) ==== Some("foo", None)
    }
  }
}
