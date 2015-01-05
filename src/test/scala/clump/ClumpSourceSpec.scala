package clump

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Future
import org.mockito.Mockito._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import com.twitter.util.Await

@RunWith(classOf[JUnitRunner])
class ClumpSourceSpec extends Spec {

  trait Context extends Scope {
    trait TestRepository {
      def fetch(inputs: Set[Int]): Future[Map[Int, Int]]
    }

    val repo = smartMock[TestRepository]
  }

  "allows to create a clump source (ClumpSource.from)" >> {
    "set input" in {
      def fetch(inputs: Set[Int]) = Future.value(inputs.map(i => i -> i.toString).toMap)
      val source = ClumpSource.from(fetch)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "list input" in {
      def fetch(inputs: List[Int]) = Future.value(inputs.map(i => i -> i.toString).toMap)
      val source = ClumpSource.from(fetch)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
  }

  "allows to create a clump source with key function (ClumpSource.apply)" >> {
    "set input" in {
      def fetch(inputs: Set[Int]) = Future.value(inputs.map(_.toString))
      val source = ClumpSource.apply(fetch)(_.toInt)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "seq input" in {
      def fetch(inputs: Seq[Int]) = Future.value(inputs.map(_.toString))
      val source = ClumpSource.apply(fetch)(_.toInt)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
  }

  "allows to create a clump source with zip as the key function (ClumpSource.zip)" in {
    def fetch(inputs: List[Int]) = Future.value(inputs.map(_.toString))
    val source = ClumpSource.zip(fetch)
    clumpResult(source.get(1)) mustEqual Some("1")
  }

  "fetches an individual clump" in new Context {
    val source = ClumpSource.from(repo.fetch)

    when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

    clumpResult(source.get(1)) mustEqual Some(2)

    verify(repo).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }

  "fetches multiple clumps" >> {

    "using list" in new Context {
      val source = ClumpSource.from(repo.fetch)

      when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

      val clump = source.get(List(1, 2))

      clumpResult(clump) mustEqual Some(List(10, 20))

      verify(repo).fetch(Set(1, 2))
      verifyNoMoreInteractions(repo)
    }

    "using varargs" in new Context {
      val source = ClumpSource.from(repo.fetch)

      when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

      val clump = source.get(1, 2)

      clumpResult(clump) mustEqual Some(List(10, 20))

      verify(repo).fetch(Set(1, 2))
      verifyNoMoreInteractions(repo)
    }
  }

  "can be used as a singleton" in new Context {
    val source = ClumpSource.from(repo.fetch)

    when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

    val future =
      Future.collect {
        for (i <- 0 until 5) yield {
          source.get(List(1)).get
        }
      }

    Await.result(future)

    verify(repo).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }
}