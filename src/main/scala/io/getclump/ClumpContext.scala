package io.getclump

import scala.collection.mutable.HashMap

private[getclump] final class ClumpContext {

  private[this] val fetchers =
    new HashMap[ClumpSource[_, _], ClumpFetcher[_, _]]()

  def flush(clumps: List[Clump[_]]): Future[Unit] =
    clumps match {
      case Nil => Future.Unit
      case _ =>
        flushUpstream(clumps).flatMap { _ =>
          flushFetches(clumps).flatMap { _ =>
            flushDownstream(clumps)
          }
        }
    }

  private[this] def flushUpstream(clumps: List[Clump[_]]) =
    flush(clumps.map(_.upstream).flatten)

  private[this] def flushDownstream(clumps: List[Clump[_]]) =
    Future.collect(clumps.map(_.downstream)).flatMap { down =>
      flush(down.flatten.toList)
    }

  private[this] def flushFetches(clumps: List[Clump[_]]) = {
    val fetches = filterFetches(clumps)
    val byFetcher = fetches.groupBy(fetch => fetcherFor(fetch.source))
    for ((fetcher, fetches) <- byFetcher)
      fetches.foreach(_.attachTo(fetcher))
    Future.collect(byFetcher.keys.map(_.flush).toSeq)
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
