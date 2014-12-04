package lilo

import com.twitter.util.Future

class LiloSource[T, U](fetch: List[T] => Future[Map[T, U]], maxBatchSize: Int) {

  def this(fetch: List[T] => Future[List[U]], keyFn: U => T, maxBatchSize: Int) = {
    this(fetch.andThen(_.map(_.map(v => (keyFn(v), v)).toMap)), maxBatchSize)
  }

  private var pending = Set[T]()
  private var fetched = Map[T, Future[Option[U]]]()

  def get(input: T): Lilo[U] = {
    synchronized {
      retryFailures(input)
      pending += input
    }
    new LiloFetch(input, this)
  }

  def get(inputs: List[T]): Lilo[List[U]] = Lilo.collect(inputs.map(get))

  private[lilo] def run(input: T) = fetched.getOrElse(input, flushAndGet(input))

  private def flushAndGet(input: T): Future[Option[U]] = flush.flatMap { _ => fetched(input) }

  private def flush =
    synchronized {
      val toFetch = pending -- fetched.keys
      val fetch = fetchInBatches(toFetch)
      pending = Set()
      fetch
    }

  private def fetchInBatches(toFetch: Set[T]) =
    Future.collect {
      toFetch.grouped(maxBatchSize).toList.map { batch =>
        val results = fetch(batch.toList)
        for (input <- batch)
          fetched += input -> results.map(_.get(input))
        results
      }
    }

  private def retryFailures(input: T) =
    fetched.get(input).map { result =>
      if (result.poll.forall(_.isThrow))
        fetched -= input
    }
}
