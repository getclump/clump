package clump

import com.twitter.util.Future

class ClumpSource[T, U](val fetch: List[T] => Future[Map[T, U]], val maxBatchSize: Int) {
  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] = {
    val fetcher = ClumpContext().fetcherFor(this)
    fetcher.append(input)
    new ClumpFetch(input, fetcher)
  }
}

object ClumpSource {
  def zip[T, U](fetch: List[T] => Future[List[U]], maxBatchSize: Int): ClumpSource[T, U] = {
    val wrapperFunction: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    new ClumpSource(wrapperFunction, maxBatchSize)
  }

  // TODO totally unreadable and crazy
  def apply[T, U](fetch: Set[T] => Future[Iterable[U]], keyFn: U => T, maxBatchSize: Int) = {
    val listToSet: List[T] => Set[T] = _.toSet
    val listToFutureIterable: List[T] => Future[Iterable[U]] = listToSet.andThen(fetch)
    val futureIterableToFutureList: Future[Iterable[U]] => Future[List[U]] = _.map(_.toList)
    val listToFutureList: List[T] => Future[List[U]] = listToFutureIterable.andThen(futureIterableToFutureList)
    new ClumpSource(listToFutureList.andThen(_.map(_.map(v => (keyFn(v), v)).toMap)), maxBatchSize)
  }

  def from[T, U](fetch: Set[T] => Future[Map[T, U]], maxBatchSize: Int) = {
    val listToSet: List[T] => Set[T] = _.toSet
    val listToFutureMap: List[T] => Future[Map[T, U]] = listToSet.andThen(fetch)
    new ClumpSource(listToFutureMap, maxBatchSize)
  }
}
