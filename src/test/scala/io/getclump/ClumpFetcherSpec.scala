package io.getclump

import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.when
import org.specs2.specification.Scope
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpFetcherSpec extends Spec {

  trait SetContext extends Scope {

    trait TestRepository {
      def fetch(inputs: Set[Int]): Future[Map[Int, Int]]
    }

    val repo = smartMock[TestRepository]
  }

  trait ListContext extends Scope {

    trait TestRepository {
      def fetch(inputs: List[Int]): Future[List[String]]
    }

    val repo = smartMock[TestRepository]
  }

  "memoizes the results of previous fetches" in new SetContext {
    val source = Clump.source(repo.fetch _)

    when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(Set(3))).thenReturn(Future(Map(3 -> 30)))

    val clump1 = Clump.traverse(List(1, 2))(source.get)
    val clump2 = Clump.traverse(List(2, 3))(source.get)

    val clump =
      for {
        v1 <- clump1
        v2 <- clump2
      } yield (v1, v2)

    clumpResult(clump) mustEqual Some((List(10, 20), List(20, 30)))

    verify(repo).fetch(Set(1, 2))
    verify(repo).fetch(Set(3))
    verifyNoMoreInteractions(repo)
  }

  "limits the batch size" in new SetContext {
    val source = Clump.source(repo.fetch _).maxBatchSize(2)

    when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))
    when(repo.fetch(Set(3))).thenReturn(Future(Map(3 -> 30)))

    val clump = Clump.traverse(List(1, 2, 3))(source.get)

    clumpResult(clump) mustEqual Some(List(10, 20, 30))

    verify(repo).fetch(Set(1, 2))
    verify(repo).fetch(Set(3))
    verifyNoMoreInteractions(repo)
  }

  "retries failed fetches" >> {
    "success (below the retries limit)" in new SetContext {
      val source =
        Clump.source(repo.fetch _).maxRetries {
          case e: IllegalStateException => 1
        }

      when(repo.fetch(Set(1)))
        .thenReturn(Future.failed(new IllegalStateException))
        .thenReturn(Future(Map(1 -> 10)))

      clumpResult(source.get(1)) mustEqual Some(10)

      verify(repo, times(2)).fetch(Set(1))
      verifyNoMoreInteractions(repo)
    }

    "failure (above the retries limit)" in new SetContext {
      val source =
        Clump.source(repo.fetch _).maxRetries {
          case e: IllegalStateException => 1
        }

      when(repo.fetch(Set(1)))
        .thenReturn(Future.failed(new IllegalStateException))
        .thenReturn(Future.failed(new IllegalStateException))

      clumpResult(source.get(1)) must throwA[IllegalStateException]

      verify(repo, times(2)).fetch(Set(1))
      verifyNoMoreInteractions(repo)
    }
  }

  "honours call order for fetches" in new ListContext {
    val source = Clump.source(repo.fetch _)(_.toInt)
    when(repo.fetch(List(1, 2, 3))).thenReturn(Future(List("1", "2", "3")))
    when(repo.fetch(List(1, 3, 2))).thenReturn(Future(List("1", "3", "2")))

    clumpResult(Clump.traverse(List(1, 2, 3))(source.get))
    verify(repo).fetch(List(1, 2, 3))

    clumpResult(Clump.traverse(List(1, 3, 2))(source.get))
    verify(repo).fetch(List(1, 3, 2))
  }
}