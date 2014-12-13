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

  "fetches an individual clump" in new Context {
    val source = Clump.sourceFrom(repo.fetch)

    when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

    clumpResult(source.get(1)) mustEqual Some(2)

    verify(repo).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }

  "fetches multiple clumps" in new Context {
    val source = Clump.sourceFrom(repo.fetch)

    when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

    val clump = source.get(List(1, 2))

    clumpResult(clump) mustEqual Some(List(10, 20))

    verify(repo).fetch(Set(1, 2))
    verifyNoMoreInteractions(repo)
  }

  "can be used as a singleton" in new Context {
    val source = Clump.sourceFrom(repo.fetch)

    when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

    val future =
      Future.collect {
        for (i <- 0 until 5) yield {
          Future.Unit.flatMap { _ =>
            source.get(List(1)).run
          }
        }
      }

    Await.result(future)

    verify(repo, times(5)).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }
}