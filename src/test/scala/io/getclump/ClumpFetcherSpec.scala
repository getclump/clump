package io.getclump

import utest._

object ClumpFetcherSpec extends Spec {

  val tests = TestSuite {

    "memoizes the results of previous fetches" - {
      object repo {
        def fetch(inputs: List[Int]) =
          inputs match {
            case List(1, 2) => Future(Map(1 -> 10, 2 -> 20))
            case List(3)    => Future(Map(3 -> 30))
          }
      }

      val source = Clump.source(repo.fetch _)
      val clump1 = Clump.traverse(List(1, 2))(source.get)
      val clump2 = Clump.traverse(List(2, 3))(source.get)

      val clump =
        for {
          v1 <- clump1
          v2 <- clump2
        } yield (v1, v2)

      assert(clumpResult(clump) == Some((List(10, 20), List(20, 30))))
    }

    "limits the batch size" - {
      object repo {
        def fetch(inputs: List[Int]) =
          inputs match {
            case List(1, 2) => Future(Map(1 -> 10, 2 -> 20))
            case List(3)    => Future(Map(3 -> 30))
          }
      }

      val source = Clump.source(repo.fetch _).maxBatchSize(2)

      val clump = Clump.traverse(List(1, 2, 3))(source.get)

      assert(clumpResult(clump) == Some(List(10, 20, 30)))
    }

    "retries failed fetches" - {
      "success (below the retries limit)" - {
        object repo {
          private var firstCall = true
          def fetch(inputs: List[Int]) =
            if (firstCall) {
              firstCall = false
              Future.failed(new IllegalStateException)
            } else
              inputs match {
                case List(1) => Future(Map(1 -> 10))
              }
        }

        val source =
          Clump.source(repo.fetch _).maxRetries {
            case e: IllegalStateException => 1
          }

        assert(clumpResult(source.get(1)) == Some(10))
      }

      "failure (above the retries limit)" - {
        object repo {
          def fetch(inputs: List[Int]): Future[Map[Int, Int]] =
            Future.failed(new IllegalStateException)
        }

        val source =
          Clump.source(repo.fetch _).maxRetries {
            case e: IllegalStateException => 1
          }

        intercept[IllegalStateException] {
          clumpResult(source.get(1))
        }
      }
    }

    "honours call order for fetches" - {
      object repo {
        def fetch(inputs: List[Int]) =
          inputs match {
            case List(1, 2, 3) => Future(List("1", "2", "3"))
            case List(1, 3, 2) => Future(List("1", "3", "2"))
          }
      }
      val source = Clump.source(repo.fetch _)(_.toInt)

      clumpResult(Clump.traverse(List(1, 2, 3))(source.get))
      clumpResult(Clump.traverse(List(1, 3, 2))(source.get))
    }
  }
}