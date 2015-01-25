package clump

import com.twitter.util.Future
import scala.collection.mutable.{ Map => MutableMap }
import com.twitter.util._

private[clump] final class ClumpFetcher[T, U](source: ClumpSource[T, U]) {

  private val fetches = MutableMap[T, Promise[Option[U]]]()

  def get(input: T) =
    synchronized {
      retryFailedFetches(input)
      fetches.getOrElseUpdate(input, Promise[Option[U]])
    }

  def flush =
    synchronized {
      Future.collect(flushInBatches).unit
    }

  private def flushInBatches =
    pendingFetches
      .grouped(source.maxBatchSize)
      .toList
      .map(fetchBatch)

  private def fetchBatch(batch: Set[T]) = {
    val results = source.fetch(batch)
    for (input <- batch) {
      val fetch = fetches(input)
      val fetchResult = results.map(_.get(input))
      fetchResult.proxyTo(fetch)
    }
    results
  }

  private def pendingFetches =
    fetches.collect {
      case (key, fetch) if (!fetch.poll.isDefined) => key
    }.toSet

  private def retryFailedFetches(input: T) =
    fetches.get(input).flatMap(_.poll).collect {
      case Throw(_) => fetches -= input
    }
}
