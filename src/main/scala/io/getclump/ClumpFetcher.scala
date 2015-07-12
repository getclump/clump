package io.getclump

import scala.collection.mutable
import scala.concurrent.ExecutionContext

private[getclump] final class ClumpFetcher[T, U](source: ClumpSource[T, U]) {

  private[this] val fetches = mutable.LinkedHashMap[T, Promise[Option[U]]]()

  def get(input: T): Future[Option[U]] =
    synchronized {
      fetches.getOrElseUpdate(input, Promise[Option[U]]).future
    }

  def flush(implicit executionContext: ExecutionContext): Future[Unit] =
    synchronized {
      Future.sequence(flushInBatches).map(_ => ())
    }

  private[this] def flushInBatches(implicit executionContext: ExecutionContext) =
    pendingFetches
      .grouped(source.maxBatchSize)
      .toList
      .map(fetchBatch)

  private[this] def fetchBatch(batch: List[T])(implicit executionContext: ExecutionContext) = {
    val results = fetchWithRetries(batch, 0)
    for (input <- batch) {
      val fetch = fetches(input)
      val fetchResult = results.map(_.get(input))
      fetchResult.onComplete(fetch.complete)
    }
    results
  }

  private[this] def fetchWithRetries(batch: List[T], retries: Int)(implicit executionContext: ExecutionContext): Future[Map[T, U]] =
    source.fetch(batch).recoverWith {
      case exception: Throwable if (maxRetries(exception) > retries) =>
        fetchWithRetries(batch, retries + 1)
    }

  private[this] def maxRetries(exception: Throwable) =
    source._maxRetries.lift(exception).getOrElse(0)

  private[this] def pendingFetches =
    fetches.collect {
      case (key, fetch) if (!fetch.isCompleted) => key
    }.toList
}
