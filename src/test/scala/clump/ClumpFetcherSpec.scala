package clump

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.twitter.util.Future
import org.mockito.Mockito._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpFetcherSpec extends Spec {

  trait Context extends Scope {
    trait TestRepository {
      def fetch(inputs: Set[Int]): Future[Map[Int, Int]]
    }

    val repo = smartMock[TestRepository]
  }

  "memoizes the results of previous fetches" in new Context {
    val source = Clump.sourceFrom(repo.fetch)

    when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(Set(3))).thenReturn(Future(Map(3 -> 30)))

    val clump1 = Clump.traverse(List(1, 2))(source.apply)

    clumpResult(clump1) mustEqual Some(List(10, 20))

    val clump2 = Clump.traverse(List(2, 3))(source.apply)

    clumpResult(clump2) mustEqual Some(List(20, 30))

    verify(repo).fetch(Set(1, 2))
    verify(repo).fetch(Set(3))
    verifyNoMoreInteractions(repo)
  }

  "limits the batch size" in new Context {
    val source = Clump.sourceFrom(repo.fetch).maxBatchSize(2)

    when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(Set(3))).thenReturn(Future(Map(3 -> 30)))

    val clump = Clump.traverse(List(1, 2, 3))(source.apply)

    clumpResult(clump) mustEqual Some(List(10, 20, 30))

    verify(repo).fetch(Set(1, 2))
    verify(repo).fetch(Set(3))
    verifyNoMoreInteractions(repo)
  }

  "retries failed fetches" in new Context {
    val source = Clump.sourceFrom(repo.fetch)

    when(repo.fetch(Set(1)))
      .thenReturn(Future.exception(new IllegalStateException))
      .thenReturn(Future(Map(1 -> 10)))

    clumpResult(source(1)) must throwA[IllegalStateException]
    clumpResult(source(1)) mustEqual Some(10)

    verify(repo, times(2)).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }
}