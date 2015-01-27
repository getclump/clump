package clump

import com.twitter.util.Future
import scala.collection.mutable.{ Map => MutableMap }
import com.twitter.util._

private[clump] final class ClumpFetcher[T, U](source: ClumpSource[T, U]) {

  private val fetches = MutableMap[T, Promise[Option[U]]]()

  def get(input: T) =
    synchronized {
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
    val results = fetchWithRetries(batch, 0)
    for (input <- batch) {
      val fetch = fetches(input)
      val fetchResult = results.map(_.get(input))
      fetchResult.proxyTo(fetch)
    }
    results
  }

  private def fetchWithRetries(batch: Set[T], retries: Int): Future[Map[T, U]] =
    source.fetch(batch).rescue {
      case exception if (maxRetries(exception) > retries) =>
        fetchWithRetries(batch, retries + 1)
    }

  private def maxRetries(exception: Throwable) =
    source._maxRetries.lift(exception).getOrElse(0)

  private def pendingFetches =
    fetches.collect {
      case (key, fetch) if (!fetch.poll.isDefined) => key
    }.toSet
}
