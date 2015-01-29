package clump

import com.twitter.util.{Future, Local}

import scala.collection.mutable.HashMap

private[clump] final class ClumpContext {

  private val fetchers =
    new HashMap[FunctionIdentity, ClumpFetcher[_, _]]()

  def fetcherFor[T, U](source: ClumpSource[T, U]) =
    synchronized {
      fetchers
        .getOrElseUpdate(source.functionIdentity, new ClumpFetcher(source))
        .asInstanceOf[ClumpFetcher[T, U]]
    }

  def flush(clumps: List[Clump[_]]): Future[Unit] =
    clumps match {
      case Nil => Future.Unit
      case clumps =>
        flushUpstream(clumps).flatMap { _ =>
          flushFetches(clumps).flatMap { _ =>
            flushDownstream(clumps)
          }
        }
    }

  private def flushUpstream(clumps: List[Clump[_]]) =
    flush(clumps.map(_.upstream).flatten)

  private def flushDownstream(clumps: List[Clump[_]]) =
    Future.collect(clumps.map(_.downstream)).flatMap { down =>
      flush(down.flatten.toList)
    }

  private def flushFetches(clumps: List[Clump[_]]) =
    Future.collect(fetchersFor(clumps).map(_.flush))

  private def fetchersFor(clumps: List[Clump[_]]) =
    clumps.collect {
      case clump: ClumpFetch[_, _] => clump.fetcher
    }.distinct
}

private[clump] object ClumpContext {

  private val local = new Local[ClumpContext]

  def apply() =
    local().getOrElse {
      val context = new ClumpContext
      local.set(Some(context))
      context
    }
}
