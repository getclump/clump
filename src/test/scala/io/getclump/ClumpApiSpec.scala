package io.getclump

import utest._

object ClumpApiSpec extends Spec {

  val tests = TestSuite {

    "the Clump object" - {

      "allows to create a constant clump" - {

        "from a future (Clump.future)" - {

          "success" - {
            "optional" - {
              "defined" - {
                assertResult(Clump.future(Future.successful(Some(1))), Some(1))
              }
              "undefined" - {
                assertResult(Clump.future(Future.successful(None)), None)
              }
            }
            "non-optional" - {
              assertResult(Clump.future(Future.successful(1)), Some(1))
            }
          }

          "failure" - {
            assertFailure[IllegalStateException] {
              Clump.future(Future.failed(new IllegalStateException))
            }
          }
        }

        "from a value (Clump.apply)" - {
          "propogates exceptions" - {
            val clump = Clump { throw new IllegalStateException }
            assertFailure[IllegalStateException] {
              clump
            }
          }

          "no exception" - {
            assertResult(Clump(1), Some(1))
          }
        }

        "from a value (Clump.value)" - {
          assertResult(Clump.value(1), Some(1))
        }

        "from a value (Clump.successful)" - {
          assertResult(Clump.successful(1), Some(1))
        }

        "from an option (Clump.value)" - {

          "defined" - {
            assertResult(Clump.value(Option(1)), Option(1))
          }

          "empty" - {
            assertResult(Clump.value(None), None)
          }
        }

        "failed (Clump.exception)" - {
          assertFailure[IllegalStateException] {
            Clump.exception(new IllegalStateException)
          }
        }

        "failed (Clump.failed)" - {
          assertFailure[IllegalStateException] {
            Clump.failed(new IllegalStateException)
          }
        }
      }

      "allows to create a clump traversing multiple inputs (Clump.traverse)" - {
        "list" - {
          val inputs = List(1, 2, 3)
          val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
          assertResult(clump, Some(List(2, 3, 4)))
        }
        "set" - {
          val inputs = Set(1, 2, 3)
          val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
          assertResult(clump, Some(Set(2, 3, 4)))
        }
        "seq" - {
          val inputs = Seq(1, 2, 3)
          val clump = Clump.traverse(inputs)(i => Clump.value(i + 1))
          assertResult(clump, Some(Seq(2, 3, 4)))
        }
      }

      "allows to collect multiple clumps - only one (Clump.collect)" - {
        "list" - {
          val clumps = List(Clump.value(1), Clump.value(2))
          assertResult(Clump.collect(clumps), Some(List(1, 2)))
        }
        "set" - {
          val clumps = Set(Clump.value(1), Clump.value(2))
          assertResult(Clump.collect(clumps), Some(Set(1, 2)))
        }
        "seq" - {
          val clumps = Seq(Clump.value(1), Clump.value(2))
          assertResult(Clump.collect(clumps), Some(Seq(1, 2)))
        }
      }

      "allows to create an empty Clump (Clump.empty)" - {
        assertResult(Clump.empty, None)
      }

      "allows to join clumps" - {

        def c(int: Int) = Clump.value(int)

        "2 instances" - {
          val clump = Clump.join(c(1), c(2))
          assertResult(clump, Some(1, 2))
        }
        "3 instances" - {
          val clump = Clump.join(c(1), c(2), c(3))
          assertResult(clump, Some(1, 2, 3))
        }
        "4 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4))
          assertResult(clump, Some(1, 2, 3, 4))
        }
        "5 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5))
          assertResult(clump, Some(1, 2, 3, 4, 5))
        }
        "6 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6))
          assertResult(clump, Some(1, 2, 3, 4, 5, 6))
        }
        "7 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7))
          assertResult(clump, Some(1, 2, 3, 4, 5, 6, 7))
        }
        "8 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8))
          assertResult(clump, Some(1, 2, 3, 4, 5, 6, 7, 8))
        }
        "9 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8), c(9))
          assertResult(clump, Some(1, 2, 3, 4, 5, 6, 7, 8, 9))
        }
        "10 instances" - {
          val clump = Clump.join(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8), c(9), c(10))
          assertResult(clump, Some(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        }
      }
    }

    "a Clump instance" - {

      "can be mapped to a new clump" - {

        "using simple a value transformation (clump.map)" - {
          assertResult(Clump.value(1).map(_ + 1), Some(2))
        }

        "using a transformation that creates a new clump (clump.flatMap)" - {
          "both clumps are defined" - {
            assertResult(Clump.value(1).flatMap(i => Clump.value(i + 1)), Some(2))
          }
          "initial clump is undefined" - {
            assertResult(Clump.value(None).flatMap(i => Clump.value(2)), None)
          }
        }
      }

      "can be joined with another clump and produce a new clump with the value of both (clump.join)" - {
        "both clumps are defined" - {
          assertResult(Clump.value(1).join(Clump.value(2)), Some(1, 2))
        }
        "one of them is undefined" - {
          assertResult(Clump.value(1).join(Clump.value(None)), None)
        }
      }

      "allows to recover from failures" - {

        "using a function that recovers using a new value (clump.handle)" - {
          "exception happens" - {
            val clump =
              Clump.exception(new IllegalStateException).handle {
                case e: IllegalStateException => Some(2)
              }
            assertResult(clump, Some(2))
          }
          "exception doesn't happen" - {
            val clump =
              Clump.value(1).handle {
                case e: IllegalStateException => None
              }
            assertResult(clump, Some(1))
          }
          "exception isn't caught" - {
            val clump =
              Clump.exception(new NullPointerException).handle {
                case e: IllegalStateException => Some(1)
              }
            assertFailure[NullPointerException] {
              clump
            }
          }
        }

        "using a function that recovers using a new value (clump.recover)" - {
          "exception happens" - {
            val clump =
              Clump.exception(new IllegalStateException).recover {
                case e: IllegalStateException => Some(2)
              }
            assertResult(clump, Some(2))
          }
          "exception doesn't happen" - {
            val clump =
              Clump.value(1).recover {
                case e: IllegalStateException => None
              }
            assertResult(clump, Some(1))
          }
          "exception isn't caught" - {
            val clump =
              Clump.exception(new NullPointerException).recover {
                case e: IllegalStateException => Some(1)
              }
            assertFailure[NullPointerException] {
              clump
            }
          }
        }

        "using a function that recovers the failure using a new clump (clump.rescue)" - {
          "exception happens" - {
            val clump =
              Clump.exception(new IllegalStateException).rescue {
                case e: IllegalStateException => Clump.value(2)
              }
            assertResult(clump, Some(2))
          }
          "exception doesn't happen" - {
            val clump =
              Clump.value(1).rescue {
                case e: IllegalStateException => Clump.value(None)
              }
            assertResult(clump, Some(1))
          }
          "exception isn't caught" - {
            val clump =
              Clump.exception(new NullPointerException).rescue {
                case e: IllegalStateException => Clump.value(1)
              }
            assertFailure[NullPointerException] {
              clump
            }
          }
        }

        "using a function that recovers the failure using a new clump (clump.recoverWith)" - {
          "exception happens" - {
            val clump =
              Clump.exception(new IllegalStateException).recoverWith {
                case e: IllegalStateException => Clump.value(2)
              }
            assertResult(clump, Some(2))
          }
          "exception doesn't happen" - {
            val clump =
              Clump.value(1).recoverWith {
                case e: IllegalStateException => Clump.value(None)
              }
            assertResult(clump, Some(1))
          }
          "exception isn't caught" - {
            val clump =
              Clump.exception(new NullPointerException).recoverWith {
                case e: IllegalStateException => Clump.value(1)
              }
            assertFailure[NullPointerException] {
              clump
            }
          }
        }

        "using a function that recovers using a new value (clump.fallback) on any exception" - {
          "exception happens" - {
            val clump = Clump.exception(new IllegalStateException).fallback(Some(1))
            assertResult(clump, Some(1))
          }

          "exception doesn't happen" - {
            val clump = Clump.value(1).fallback(Some(2))
            assertResult(clump, Some(1))
          }
        }

        "using a function that recovers using a new clump (clump.fallbackTo) on any exception" - {
          "exception happens" - {
            val clump = Clump.exception(new IllegalStateException).fallbackTo(Clump.value(1))
            assertResult(clump, Some(1))
          }

          "exception doesn't happen" - {
            val clump = Clump.value(1).fallbackTo(Clump.value(2))
            assertResult(clump, Some(1))
          }
        }
      }

      "can have its result filtered (clump.filter)" - {
        assertResult(Clump.value(1).filter(_ != 1), None)
        assertResult(Clump.value(1).filter(_ == 1), Some(1))
      }

      "uses a covariant type parameter" - {
        trait A
        class B extends A
        class C extends A
        val clump: Clump[List[A]] = Clump.traverse(List(new B, new C))(Clump.value(_))
      }

      "allows to defined a fallback value (clump.orElse)" - {
        "undefined" - {
          assertResult(Clump.empty.orElse(1), Some(1))
        }
        "defined" - {
          assertResult(Clump.value(Some(1)).orElse(2), Some(1))
        }
      }

      "allows to defined a fallback clump (clump.orElse)" - {
        "undefined" - {
          assertResult(Clump.empty.orElse(Clump.value(1)), Some(1))
        }
        "defined" - {
          assertResult(Clump.value(Some(1)).orElse(Clump.value(2)), Some(1))
        }
      }

      "can represent its result as a collection (clump.list) when its type is a collection" - {
        "list" - {
          Clump.value(List(1, 2)).list.map(result => assert(result == List(1, 2)))
        }
        "set" - {
          Clump.value(Set(1, 2)).list.map(result => assert(result == Set(1, 2)))
        }
        "seq" - {
          Clump.value(Seq(1, 2)).list.map(result => assert(result == Seq(1, 2)))
        }
        "not a collection" - {
          compileError("Clump.value(1).flatten")
        }
      }

      "can provide a result falling back to a default (clump.getOrElse)" - {
        "initial clump is undefined" - {
          Clump.value(None).getOrElse(1).map(result => assert(result == 1))
        }

        "initial clump is defined" - {
          Clump.value(Some(2)).getOrElse(1).map(result => assert(result == 2))
        }
      }

      "has a utility method (clump.apply) for unwrapping optional result" - {
        Clump.value(1).apply().map(result => assert(result == 1))
        assertFailure[NoSuchElementException] {
          Clump.value[Int](None)()
        }
      }

      "can be made optional (clump.optional) to avoid lossy joins" - {
        val clump: Clump[String] = Clump.empty
        val optionalClump: Clump[Option[String]] = clump.optional
        assertResult(optionalClump, Some(None))

        val valueClump: Clump[String] = Clump.value("foo")
        assertResult(valueClump.join(clump), None)
        assertResult(valueClump.join(optionalClump), Some("foo", None))
      }
    }
  }
}
