package io.getclump

import scala.collection.mutable
import scala.concurrent.ExecutionContext

private[getclump] final class ClumpFetcher[T, U](source: ClumpSource[T, U]) {

  private[this] val fetches = mutable.LinkedHashMap.empty[T, Promise[U]]

  def get(input: T): Future[U] =
    synchronized {
      fetches.getOrElseUpdate(input, Promise[U]).future
    }

  def flush(implicit ec: ExecutionContext): Future[Unit] =
    synchronized {
      Future.sequence(flushInBatches).map(_ => ())
    }

  private[this] def flushInBatches(implicit ec: ExecutionContext) =
    pendingFetches
      .grouped(source.maxBatchSize)
      .toList
      .map(fetchBatch)

  private[this] def fetchBatch(batch: List[T])(implicit ec: ExecutionContext) = {
    val results = fetchWithRetries(batch, 0)
    for (input <- batch) {
      val fetch = fetches(input)
      val fetchResult = results.map(_.getOrElse(input, throw ClumpNoSuchElementException(s"No key $input")).get)
      fetchResult.onComplete(fetch.complete)
    }
    results
  }

  private[this] def fetchWithRetries(batch: List[T], retries: Int)(implicit ec: ExecutionContext): Future[Map[T, Try[U]]] =
    source.fetch(batch).flatMap { results =>
      // If there are individual failures then just retry those entries
      val (toRetry, noRetry) = results.partition {
        case (_, Success(_)) => false
        case (_, Failure(e)) => maxRetries(e) > retries
      }

      val toRetryFuture = if (toRetry.nonEmpty) {
        fetchWithRetries(toRetry.keys.toList, retries + 1)
      } else {
        Future.successful(Map.empty[T, Try[U]])
      }

      toRetryFuture.map(_ ++ noRetry)
    }.recoverWith {
      // If the entire fetch fails then retry the whole thing
      case exception: Throwable if maxRetries(exception) > retries =>
        fetchWithRetries(batch, retries + 1)
    }

  private[this] def maxRetries(exception: Throwable) =
    source._maxRetries.lift(exception).getOrElse(0)

  private[this] def pendingFetches =
    fetches.collect {
      case (key, fetch) if !fetch.isCompleted => key
    }.toList
}
