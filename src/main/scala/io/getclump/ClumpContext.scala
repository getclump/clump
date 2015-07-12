package io.getclump

import scala.collection.mutable
import scala.concurrent.ExecutionContext

private[getclump] final class ClumpContext {

  private[this] val fetchers = new mutable.HashMap[ClumpSource[_, _], ClumpFetcher[_, _]]()

  def flush(clumps: List[Clump[_]])(implicit ec: ExecutionContext): Future[Unit] = {
    // 1. Get a list of all visible clumps grouped by level of composition, starting at the highest level
    val upstreamByLevel = getClumpsByLevel(clumps)

    // 2. Flush the fetches from all the visible clumps
    flushFetchesInParallel(upstreamByLevel.flatten).flatMap { _ =>
      // 3. Walk through the downstream clumps as well, starting at the deepest level
      flushDownstreamByLevel(upstreamByLevel.reverse)
    }
  }

  // Unfold all visible (ie. upstream) clumps from lowest to highest level
  private[this] def getClumpsByLevel(clumps: List[Clump[_]]): List[List[Clump[_]]] = {
    clumps match {
      case Nil => Nil
      case _ => clumps :: getClumpsByLevel(clumps.flatMap(_.upstream))
    }
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
