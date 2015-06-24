package io.getclump

import utest._
import scala.collection.mutable.ListBuffer

object ClumpExecutionSpec extends Spec {

  trait Context {
    val source1Fetches = ListBuffer[Set[Int]]()
    val source2Fetches = ListBuffer[Set[Int]]()

    protected def fetchFunction(fetches: ListBuffer[Set[Int]], inputs: Set[Int]) = {
      fetches += inputs
      Future.successful(inputs.map(i => i -> i * 10).toMap)
    }

    protected val source1 = Clump.source((i: Set[Int]) => fetchFunction(source1Fetches, i))
    val source2 = Clump.source((i: Set[Int]) => fetchFunction(source2Fetches, i))
  }

  val tests = TestSuite {
    "batches requests" - {

      "for multiple clumps created from traversed inputs" - new Context {
        val clump =
          Clump.traverse(List(1, 2, 3, 4)) {
            i =>
              if (i <= 2)
                source1.get(i)
              else
                source2.get(i)
          }

        assert(clumpResult(clump) == Some(List(10, 20, 30, 40)))
        assert(source1Fetches == List(Set(1, 2)))
        assert(source2Fetches == List(Set(3, 4)))
      }

      "for multiple clumps collected into only one clump" - new Context {
        val clump = Clump.collect(source1.get(1), source1.get(2), source2.get(3), source2.get(4))

        assert(clumpResult(clump) == Some(List(10, 20, 30, 40)))
        assert(source1Fetches == List(Set(1, 2)))
        assert(source2Fetches == List(Set(3, 4)))
      }

      "for clumps created inside nested flatmaps" - new Context {
        val clump1 = Clump.value(1).flatMap(source1.get(_)).flatMap(source2.get(_))
        val clump2 = Clump.value(2).flatMap(source1.get(_)).flatMap(source2.get(_))

        assert(clumpResult(Clump.collect(clump1, clump2)) == Some(List(100, 200)))
        assert(source1Fetches == List(Set(1, 2)))
        assert(source2Fetches == List(Set(20, 10)))
      }

      "for clumps composed using for comprehension" - {

        "one level" - new Context {
          val clump =
            for {
              int <- Clump.collect(source1.get(1), source1.get(2), source2.get(3), source2.get(4))
            } yield int

          assert(clumpResult(clump) == Some(List(10, 20, 30, 40)))
          assert(source1Fetches == List(Set(1, 2)))
          assert(source2Fetches == List(Set(3, 4)))
        }

        "two levels" - new Context {
          val clump =
            for {
              ints1 <- Clump.collect(source1.get(1), source1.get(2))
              ints2 <- Clump.collect(source2.get(3), source2.get(4))
            } yield (ints1, ints2)

          assert(clumpResult(clump) == Some(List(10, 20), List(30, 40)))
          assert(source1Fetches == List(Set(1, 2)))
          assert(source2Fetches == List(Set(3, 4)))
        }

        "with a filter condition" - new Context {
          val clump =
            for {
              ints1 <- Clump.collect(source1.get(1), source1.get(2))
              int2 <- source2.get(3) if (int2 != 999)
            } yield (ints1, int2)

          assert(clumpResult(clump) == Some(List(10, 20), 30))
          assert(source1Fetches == List(Set(1, 2)))
          assert(source2Fetches == List(Set(3)))
        }

        "using a join" - new Context {
          val clump =
            for {
              ints1 <- Clump.collect(source1.get(1), source1.get(2))
              ints2 <- source2.get(3).join(source2.get(4))
            } yield (ints1, ints2)

          assert(clumpResult(clump) == Some(List(10, 20), (30, 40)))
          assert(source1Fetches == List(Set(1, 2)))
          assert(source2Fetches == List(Set(3, 4)))
        }

        "using a future clump as base" - new Context {
          val clump =
            for {
              int <- Clump.future(Future.successful(Some(1)))
              collect1 <- Clump.collect(source1.get(int))
              collect2 <- Clump.collect(source2.get(int))
            } yield (collect1, collect2)

          assert(clumpResult(clump) == Some((List(10), List(10))))
          assert(source1Fetches == List(Set(1)))
          assert(source2Fetches == List(Set(1)))
        }

        "complex scenario" - new Context {
          val clump =
            for {
              const1 <- Clump.value(1)
              const2 <- Clump.value(2)
              collect1 <- Clump.collect(source1.get(const1), source2.get(const2))
              collect2 <- Clump.collect(source1.get(const1), source2.get(const2)) if (true)
              (join1a, join1b) <- Clump.value(4).join(Clump.value(5))
              join2 <- source1.get(collect1).join(source2.get(join1b))
            } yield (const1, const2, collect1, collect2, (join1a, join1b), join2)

          assert(clumpResult(clump) == Some((1, 2, List(10, 20), List(10, 20), (4, 5), (List(100, 200), 50))))
          assert(source1Fetches == List(Set(1), Set(10, 20)))
          assert(source2Fetches == List(Set(2), Set(5)))
        }
      }
    }

    "executes joined clumps - parallel" - new Context {
      val promises = List(Promise[Map[Int, Int]](), Promise[Map[Int, Int]]())

      val promisesIterator = promises.iterator

      protected override def fetchFunction(fetches: ListBuffer[Set[Int]], inputs: Set[Int]) =
        promisesIterator.next.future

      val clump = source1.get(1).join(source2.get(2))

      val future: Future[Option[(Int, Int)]] = clump.get

      assert(promises.size == 2)
    }

    "short-circuits the computation - case of a failure" - new Context {
      val clump = Clump.exception[Int](new IllegalStateException).map(_ => throw new NullPointerException)
      intercept[IllegalStateException] {
        clumpResult(clump)
      }
    }
  }
}