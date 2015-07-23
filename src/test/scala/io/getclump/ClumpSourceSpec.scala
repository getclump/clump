package io.getclump

import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.when
import org.specs2.specification.Scope
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClumpSourceSpec extends Spec {

  trait Context extends Scope {
    trait TestRepository {
      def fetch(inputs: Set[Int]): Future[Map[Int, Int]]
      def fetchWithScope(fromScope: Int, inputs: Set[Int]): Future[Map[Int, Int]]
    }

    val repo = smartMock[TestRepository]
  }

  "fetches an individual clump" in new Context {
    val source = Clump.source(repo.fetch _)

    when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

    clumpResult(source.get(1)) mustEqual Some(2)

    verify(repo).fetch(Set(1))
    verifyNoMoreInteractions(repo)
  }

  "fetches multiple clumps" >> {

    "using list" in new Context {
      val source = Clump.source(repo.fetch _)

      when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

      val clump = source.get(List(1, 2))

      clumpResult(clump) ==== Some(List(10, 20))

      verify(repo).fetch(Set(1, 2))
      verifyNoMoreInteractions(repo)
    }

    "using set" in new Context {
      val source = Clump.source(repo.fetch _)

      when(repo.fetch(Set(1, 2))).thenReturn(Future(Map(1 -> 10, 2 -> 20)))

      val clump = source.get(Set(1, 2))

      clumpResult(clump) ==== Some(Set(10, 20))

      verify(repo).fetch(Set(1, 2))
      verifyNoMoreInteractions(repo)
    }
  }

  "can be used as a non-singleton" >> {
    "without values from the outer scope" in new Context {
      val source = Clump.source(repo.fetch _)

      when(repo.fetch(Set(1))).thenReturn(Future(Map(1 -> 2)))

      val clump =
        Clump.collect {
          (for (i <- 0 until 5) yield {
            source.get(List(1))
          }).toList
        }

      awaitResult(clump.get)

      verify(repo).fetch(Set(1))
      verifyNoMoreInteractions(repo)
    }

    "with values from the outer scope" in new Context {
      val scope = 1
      val source = Clump.source((inputs: Set[Int]) => repo.fetchWithScope(scope, inputs))

      when(repo.fetchWithScope(scope, Set(1))).thenReturn(Future(Map(1 -> 2)))

      val clump =
        Clump.collect {
          (for (i <- 0 until 5) yield {
            source.get(List(1))
          }).toList
        }

      awaitResult(clump.get)

      verify(repo).fetchWithScope(1, Set(1))
      verifyNoMoreInteractions(repo)
    }
  }
  
  "limits the batch size to 100 by default" in new Context {
    val source = Clump.source(repo.fetch _)
    source.maxBatchSize mustEqual 100
  }
}
