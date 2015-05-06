package io.getclump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpApiSpec extends Spec {

  "the Clump object" >> {

    "allows to create a constant clump" >> {

      "from a future (Clump.future)" >> {

        "success" >> {
          "optional" >> {
            "defined" in {
              clumpResult(Clump.future(Future.value(Some(1)))) mustEqual Some(1)
            }
            "undefined" in {
              clumpResult(Clump.future(Future.value(None))) mustEqual None
            }
          }
          "non-optional" in {
            clumpResult(Clump.future(Future.value(1))) mustEqual Some(1)
          }
        }

        "failure" in {
          clumpResult(Clump.future(Future.exception(new IllegalStateException))) must throwA[IllegalStateException]
        }
      }

      "from a value (Clump.apply)" >> {
        "propogates exceptions" in {
          val clump = Clump { throw new IllegalStateException }
          clumpResult(clump) must throwA[IllegalStateException]
        }

        "no exception" in {
          clumpResult(Clump(1)) mustEqual Some(1)
        }
      }

      "from a value (Clump.value)" in {
        clumpResult(Clump.value(1)) mustEqual Some(1)
      }

      "from a value (Clump.successful)" in {
        clumpResult(Clump.successful(1)) mustEqual Some(1)
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

      "failed (Clump.failed)" in {
        clumpResult(Clump.failed(new IllegalStateException)) must throwA[IllegalStateException]
      }
    }

    "allows to create a clump traversing multiple inputs (Clump.traverse)" in {
      "list" in {
        val inputs = List(1, 2, 3)
        val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
        clumpResult(clump) ==== Some(List(2, 3, 4))
      }
      "set" in {
        val inputs = Set(1, 2, 3)
        val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
        clumpResult(clump) ==== Some(Set(2, 3, 4))
      }
      "seq" in {
        val inputs = Seq(1, 2, 3)
        val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
        clumpResult(clump) ==== Some(Seq(2, 3, 4))
      }
    }

    "allows to collect multiple clumps in only one (Clump.collect)" >> {
      "list" in {
        val clumps = List(Clump.value(1), Clump.value(2))
        clumpResult(Clump.collect(clumps)) mustEqual Some(List(1, 2))
      }
      "set" in {
        val clumps = Set(Clump.value(1), Clump.value(2))
        clumpResult(Clump.collect(clumps)) mustEqual Some(Set(1, 2))
      }
      "seq" in {
        val clumps = Seq(Clump.value(1), Clump.value(2))
        clumpResult(Clump.collect(clumps)) mustEqual Some(Seq(1, 2))
      }
    }

    "allows to create an empty Clump (Clump.empty)" in {
      clumpResult(Clump.empty) ==== None
    }

    "allows to join clumps" >> {

      def c(int: Int) = Clump.value(int)

      "2 instances" in {
        val clump = Clump.join(c(1), c(2))
        clumpResult(clump) mustEqual Some(1, 2)
      }
      "3 instances" in {
        val clump = Clump.join(c(1), c(2), c(3))
        clumpResult(clump) mustEqual Some(1, 2, 3)
      }
      "4 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4)
      }
      "5 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5)
      }
      "6 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5, 6)
      }
      "7 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5, 6, 7)
      }
      "8 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5, 6, 7, 8)
      }
      "9 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8), c(9))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5, 6, 7, 8, 9)
      }
      "10 instances" in {
        val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8), c(9), c(10))
        clumpResult(clump) mustEqual Some(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      }
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

      "using a function that recovers using a new value (clump.recover)" >> {
        "exception happens" in {
          val clump =
            Clump.exception(new IllegalStateException).recover {
              case e: IllegalStateException => Some(2)
            }
          clumpResult(clump) mustEqual Some(2)
        }
        "exception doesn't happen" in {
          val clump =
            Clump.value(1).recover {
              case e: IllegalStateException => None
            }
          clumpResult(clump) mustEqual Some(1)
        }
        "exception isn't caught" in {
          val clump =
            Clump.exception(new NullPointerException).recover {
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

      "using a function that recovers the failure using a new clump (clump.recoverWith)" >> {
        "exception happens" in {
          val clump =
            Clump.exception(new IllegalStateException).recoverWith {
              case e: IllegalStateException => Clump.value(2)
            }
          clumpResult(clump) mustEqual Some(2)
        }
        "exception doesn't happen" in {
          val clump =
            Clump.value(1).recoverWith {
              case e: IllegalStateException => Clump.value(None)
            }
          clumpResult(clump) mustEqual Some(1)
        }
        "exception isn't caught" in {
          val clump =
            Clump.exception(new NullPointerException).recoverWith {
              case e: IllegalStateException => Clump.value(1)
            }
          clumpResult(clump) must throwA[NullPointerException]
        }
      }

      "using a function that recovers using a new value (clump.fallback) on any exception" >> {
        "exception happens" in {
          val clump = Clump.exception(new IllegalStateException).fallback(Some(1))
          clumpResult(clump) mustEqual Some(1)
        }

        "exception doesn't happen" in {
          val clump = Clump.value(1).fallback(Some(2))
          clumpResult(clump) mustEqual Some(1)
        }
      }

      "using a function that recovers using a new clump (clump.fallbackTo) on any exception" >> {
        "exception happens" in {
          val clump = Clump.exception(new IllegalStateException).fallbackTo(Clump.value(1))
          clumpResult(clump) mustEqual Some(1)
        }

        "exception doesn't happen" in {
          val clump = Clump.value(1).fallbackTo(Clump.value(2))
          clumpResult(clump) mustEqual Some(1)
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
        clumpResult(Clump.empty.orElse(1)) ==== Some(1)
      }
      "defined" in {
        clumpResult(Clump.value(Some(1)).orElse(2)) ==== Some(1)
      }
    }

    "allows to defined a fallback clump (clump.orElse)" >> {
      "undefined" in {
        clumpResult(Clump.empty.orElse(Clump.value(1))) ==== Some(1)
      }
      "defined" in {
        clumpResult(Clump.value(Some(1)).orElse(Clump.value(2))) ==== Some(1)
      }
    }

    "can represent its result as a collection (clump.list) when its type is a collection" >> {
      "list" in {
        blockOn(Clump.value(List(1, 2)).list) ==== List(1, 2)
      }
      "set" in {
        blockOn(Clump.value(Set(1, 2)).list) ==== Set(1, 2)
      }
      "seq" in {
        blockOn(Clump.value(Seq(1, 2)).list) ==== Seq(1, 2)
      }
      // Clump.value(1).flatten //doesn't compile
    }

    "can provide a result falling back to a default (clump.getOrElse)" >> {
      "initial clump is undefined" in {
        blockOn(Clump.value(None).getOrElse(1)) ==== 1
      }

      "initial clump is defined" in {
        blockOn(Clump.value(Some(2)).getOrElse(1)) ==== 2
      }
    }

    "has a utility method (clump.apply) for unwrapping optional result" in {
      blockOn(Clump.value(1).apply()) ==== 1
      blockOn(Clump.value[Int](None)()) must throwA[NoSuchElementException]
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
