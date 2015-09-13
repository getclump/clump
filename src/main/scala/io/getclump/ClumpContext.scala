package io.getclump

import scala.collection.immutable.SortedMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext

private[getclump] final class ClumpContext {

  private[this] val fetchers = mutable.HashMap.empty[ClumpSource[_, _], ClumpFetcher[_, _]]

  def flush(clumps: List[Clump[_]])(implicit ec: ExecutionContext): Future[Unit] = {
    // 1. Get a list of all visible clumps
    val upstream = getAllUpstream(clumps)

    // 2. Flush the fetches from all the visible clumps
    flushFetchesInParallel(upstream).flatMap { _ =>
      // 3. Walk through the downstream clumps as well, starting at the deepest level
      flushDownstreamByLevel(groupClumpsByLevel(upstream))
    }
  }

  // Unfold all visible (ie. upstream) clumps
  private[this] def getAllUpstream(clumps: List[Clump[_]]): List[Clump[_]] = {
    clumps match {
      case Nil => Nil
      case _ => clumps ::: getAllUpstream(clumps.flatMap(_.upstream))
    }
  }

  // Strip the leaves at the bottom of the clump tree one level at a time so that these two conditions are satisfied:
  // - Clumps appear in later lists than all their upstream children
  // - Clumps appear as early in the list as possible
  private[this] def groupClumpsByLevel(clumps: List[Clump[_]]): List[List[Clump[_]]] = {
    // 1. Get the longest distance from this Clump to the bottom of the tree (memoized function)
    val m = mutable.HashMap.empty[Clump[_], Int]
    def getDistanceFromBottom(clump: Clump[_]): Int = m.getOrElseUpdate(clump, {
      clump.upstream match {
        case Nil => 0
        case list => list.map(getDistanceFromBottom).max + 1
      }
    })

    // 2. Group clumps by these levels and return the deepest level first
    SortedMap(clumps.groupBy(getDistanceFromBottom).toSeq:_*).values.toList
  }

  private[this] def flushDownstreamByLevel(levels: List[List[Clump[_]]])(implicit ec: ExecutionContext): Future[Unit] = {
    levels match {
      case Nil => Future.successful(())
      case head :: tail =>
        // 1. Resolve the downstream clumps (will succeed because deeper downstream clumps have already been resolved)
        Future.sequence(head.map(_.downstream)).flatMap { res =>
          // 2. Flush the resulting clumps, thus completely resolving this subtree of the composition
          flush(res.flatten).flatMap { _ =>
            // 3. Move up one level of composition and repeat this process
            flushDownstreamByLevel(tail)
          }
        }
    }
  }

  // Flush all the ClumpFetch instances in a list of clumps, calling their associated fetch functions in parallel if possible
  private[this] def flushFetchesInParallel(clumps: List[Clump[_]])(implicit ec: ExecutionContext) = {
    val fetches = filterFetches(clumps)
    val byFetcher = fetches.groupBy(fetch => fetcherFor(fetch.source))
    for ((fetcher, fetches) <- byFetcher)
      fetches.foreach(_.attachTo(fetcher))
    Future.sequence(byFetcher.keys.map(_.flush)).map(_ => ())
  }

  private[this] def filterFetches(clumps: List[Clump[_]]) =
    clumps.collect {
      case clump: ClumpFetch[_, _] =>
        clump.asInstanceOf[ClumpFetch[Any, Any]]
    }

  private[this] def fetcherFor(source: ClumpSource[_, _]) =
    synchronized {
      fetchers.getOrElseUpdate(source, new ClumpFetcher(source))
        .asInstanceOf[ClumpFetcher[Any, Any]]
    }
}
