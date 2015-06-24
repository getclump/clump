package io.getclump

import utest._

object ClumpSourceSpec extends Spec {

  val tests = TestSuite {

    "fetches an individual clump" - {
      object repo {
        def fetch(inputs: List[Int]) =
          inputs match {
            case List(1) => Future(Map(1 -> 2))
          }
      }

      val source = Clump.source(repo.fetch _)

      assert(clumpResult(source.get(1)) == Some(2))
    }

    "fetches multiple clumps" - {

      "using list" - {
        object repo {
          def fetch(inputs: List[Int]) =
            inputs match {
              case List(1, 2) => Future(Map(1 -> 10, 2 -> 20))
            }
        }

        val source = Clump.source(repo.fetch _)

        val clump = source.get(List(1, 2))

        assert(clumpResult(clump) == Some(List(10, 20)))
      }

      "can be used as a non-singleton" - {
        "without values from the outer scope" - {
          object repo {
            def fetch(inputs: List[Int]) =
              inputs match {
                case List(1) => Future(Map(1 -> 2))
              }
          }
          val source = Clump.source(repo.fetch _)

          val clump =
            Clump.collect {
              for (i <- 0 until 5) yield {
                source.get(1)
              }
            }

          assert(clumpResult(clump) == Some(List(2, 2, 2, 2, 2)))
        }

        "with values from the outer scope" - {
          val scope = 1

          object repo {
            def fetchWithScope(fromScope: Int, inputs: List[Int]) =
              (fromScope, inputs) match {
                case (scope, List(1)) => Future(Map(1 -> 2))
              }
          }

          val source = Clump.source((inputs: List[Int]) => repo.fetchWithScope(scope, inputs))

          val clump =
            Clump.collect {
              for (i <- 0 until 5) yield {
                source.get(1)
              }
            }

          assert(clumpResult(clump) == Some(List(2, 2, 2, 2, 2)))
        }
      }

      "limits the batch size to 100 by default" - {
        object repo {
          def fetch(inputs: List[Int]): Future[Map[Int, Int]] = ???
        }
        val source = Clump.source(repo.fetch _)
        assert(source.maxBatchSize == 100)
      }
    }
  }
}