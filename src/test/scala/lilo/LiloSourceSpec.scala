package lilo

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Future
import org.mockito.Mockito._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class LiloSourceSpec extends Spec {

  trait Context extends Scope {
    trait TestRepository {
      def fetch(inputs: List[Int]): Future[Map[Int, Int]]
    }

    val repo = smartMock[TestRepository]
  }

  "fetches an individual lilo" in new Context {
    val source = Lilo.sourceFrom(repo.fetch)

    when(repo.fetch(List(1))).thenReturn(Future(Map(1 -> 2)))

    resultOf(source.get(1)) mustEqual Some(2)

    verify(repo).fetch(List(1))
    verifyNoMoreInteractions(repo)
  }

  "fetches multiple lilos" in new Context {
    val source = Lilo.sourceFrom(repo.fetch)

    when(repo.fetch(List(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

    val lilo = source.get(List(1, 2))

    resultOf(lilo) mustEqual Some(List(10, 20))

    verify(repo).fetch(List(1, 2))
    verifyNoMoreInteractions(repo)
  }

  "memoize the results of previous fetches" in new Context {
    val source = Lilo.sourceFrom(repo.fetch)

    when(repo.fetch(List(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(List(3))).thenReturn(Future(Map(3 -> 30)))

    val lilo1 = Lilo.traverse(List(1, 2))(source.get)

    resultOf(lilo1) mustEqual Some(List(10, 20))

    val lilo2 = Lilo.traverse(List(2, 3))(source.get)

    resultOf(lilo2) mustEqual Some(List(20, 30))

    verify(repo).fetch(List(1, 2))
    verify(repo).fetch(List(3))
    verifyNoMoreInteractions(repo)
  }

  "limits the batch size" in new Context {
    val source = Lilo.sourceFrom(repo.fetch, maxBatchSize = 2)

    when(repo.fetch(List(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(List(3))).thenReturn(Future(Map(3 -> 30)))

    val lilo = Lilo.traverse(List(1, 2, 3))(source.get)

    resultOf(lilo) mustEqual Some(List(10, 20, 30))

    verify(repo).fetch(List(1, 2))
    verify(repo).fetch(List(3))
    verifyNoMoreInteractions(repo)
  }

  "retries failed fetches" in new Context {
    val source = Lilo.sourceFrom(repo.fetch)

    when(repo.fetch(List(1)))
      .thenReturn(Future.exception(new IllegalStateException))
      .thenReturn(Future(Map(1 -> 10)))

    resultOf(source.get(1)) must throwA[IllegalStateException]
    resultOf(source.get(1)) mustEqual Some(10)

    verify(repo, times(2)).fetch(List(1))
    verifyNoMoreInteractions(repo)
  }
}