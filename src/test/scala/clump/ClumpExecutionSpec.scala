package clump

import scala.collection.mutable.ListBuffer
import org.junit.runner.RunWith
import org.specs2.specification.Scope
import com.twitter.util.Future
import com.twitter.util.JavaTimer
import com.twitter.util.Promise
import com.twitter.util.TimeConversions.intToTimeableNumber
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpExecutionSpec extends Spec {

  trait Context extends Scope {
    val source1Fetches = ListBuffer[Set[Int]]()
    val source2Fetches = ListBuffer[Set[Int]]()

    protected def fetchFunction(fetches: ListBuffer[Set[Int]], inputs: Set[Int]) = {
      fetches += inputs
      Future.value(inputs.map(i => i -> i * 10).toMap)
    }

    protected def source1 = Clump.sourceFrom((i: Set[Int]) => fetchFunction(source1Fetches, i))
    val source2 = Clump.sourceFrom((i: Set[Int]) => fetchFunction(source2Fetches, i))
  }

  "batches requests" >> {

    "for multiple clumps created from traversed inputs" in new Context {
      val clump =
        Clump.traverse(List(1, 2, 3, 4)) {
          i =>
            if (i <= 2)
              source1.get(i)
            else
              source2.get(i)
        }

      clumpResult(clump) mustEqual Some(List(10, 20, 30, 40))
      source1Fetches mustEqual List(Set(1, 2))
      source2Fetches mustEqual List(Set(3, 4))
    }

    "for multiple clumps collected into only one clump" in new Context {
      val clump = Clump.collect(source1.get(1), source1.get(2), source2.get(3), source2.get(4))

      clumpResult(clump) mustEqual Some(List(10, 20, 30, 40))
      source1Fetches mustEqual List(Set(1, 2))
      source2Fetches mustEqual List(Set(3, 4))
    }

    "for clumps created inside nested flatmaps" in new Context {
      val clump1 = Clump.value(1).flatMap(source1.get(_)).flatMap(source2.get(_))
      val clump2 = Clump.value(2).flatMap(source1.get(_)).flatMap(source2.get(_))

      clumpResult(Clump.collect(clump1, clump2)) mustEqual Some(List(100, 200))
      source1Fetches mustEqual List(Set(1, 2))
      source2Fetches mustEqual List(Set(20, 10))
    }

    "for composition branches with different latencies" in new Context {
      implicit val timer = new JavaTimer
      val clump1 =
        Clump.value(1).flatMap { int =>
          Clump.future(Future.value(Some(int)))
            .flatMap(source1.get)
        }
      val clump2 =
        Clump.value(2).flatMap { int =>
          Clump.future(Future.value(Some(int)).delayed(100 millis))
            .flatMap(source1.get)
        }

      val clump = Clump.collect(clump1, clump2)

      clumpResult(clump) mustEqual Some((List(10, 20)))
      source1Fetches mustEqual List(Set(1, 2))
    }

    "for clumps composed using for comprehension" >> {

      "one level" in new Context {
        val clump =
          for {
            int <- Clump.collect(source1.get(1), source1.get(2), source2.get(3), source2.get(4))
          } yield int

        clumpResult(clump) mustEqual Some(List(10, 20, 30, 40))
        source1Fetches mustEqual List(Set(1, 2))
        source2Fetches mustEqual List(Set(3, 4))
      }

      "two levels" in new Context {
        val clump =
          for {
            ints1 <- Clump.collect(source1.get(1), source1.get(2))
            ints2 <- Clump.collect(source2.get(3), source2.get(4))
          } yield (ints1, ints2)

        clumpResult(clump) mustEqual Some(List(10, 20), List(30, 40))
        source1Fetches mustEqual List(Set(1, 2))
        source2Fetches mustEqual List(Set(3, 4))
      }

      "with a filter condition" in new Context {
        val clump =
          for {
            ints1 <- Clump.collect(source1.get(1), source1.get(2))
            int2 <- source2.get(3) if (int2 != 999)
          } yield (ints1, int2)

        clumpResult(clump) mustEqual Some(List(10, 20), 30)
        source1Fetches mustEqual List(Set(1, 2))
        source2Fetches mustEqual List(Set(3))
      }

      "using a join" in new Context {
        val clump =
          for {
            ints1 <- Clump.collect(source1.get(1), source1.get(2))
            ints2 <- source2.get(3).join(source2.get(4))
          } yield (ints1, ints2)

        clumpResult(clump) mustEqual Some(List(10, 20), (30, 40))
        source1Fetches mustEqual List(Set(1, 2))
        source2Fetches mustEqual List(Set(3, 4))
      }

      "using a future clump as base" in new Context {
        val clump =
          for {
            int <- Clump.future(Future.value(Some(1)))
            collect1 <- Clump.collect(source1.get(int))
            collect2 <- Clump.collect(source2.get(int))
          } yield (collect1, collect2)

        clumpResult(clump) mustEqual Some((List(10), List(10)))
        source1Fetches mustEqual List(Set(1))
        source2Fetches mustEqual List(Set(1))
      }

      "complex scenario" in new Context {
        val clump =
          for {
            const1 <- Clump.value(1)
            const2 <- Clump.value(2)
            collect1 <- Clump.collect(source1.get(const1), source2.get(const2))
            collect2 <- Clump.collect(source1.get(const1), source2.get(const2)) if (true)
            (join1a, join1b) <- Clump.value(4).join(Clump.value(5))
            join2 <- source1.get(collect1).join(source2.get(join1b))
          } yield (const1, const2, collect1, collect2, (join1a, join1b), join2)

        clumpResult(clump) mustEqual Some((1, 2, List(10, 20), List(10, 20), (4, 5), (List(100, 200), 50)))
        source1Fetches mustEqual List(Set(1), Set(10, 20))
        source2Fetches mustEqual List(Set(2), Set(5))
      }
    }
  }

  "executes joined clumps in parallel" in new Context {
    val promises = List(Promise[Map[Int, Int]](), Promise[Map[Int, Int]]())

    val promisesIterator = promises.iterator

    protected override def fetchFunction(fetches: ListBuffer[Set[Int]], inputs: Set[Int]) =
      promisesIterator.next

    val clump = source1.get(1).join(source2.get(2))

    val future: Future[Option[(Int, Int)]] = clump.get

    promises.size mustEqual 2
  }

  "short-circuits the computation in case of a failure" in new Context {
    val clump = Clump.exception[Int](new IllegalStateException).map(_ => throw new NullPointerException)
    clumpResult(clump) must throwA[IllegalStateException]
  }
}