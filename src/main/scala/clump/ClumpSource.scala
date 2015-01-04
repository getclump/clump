package clump

import com.twitter.util.Future

case class ClumpSource[T, U] private (val fetch: Set[T] => Future[Map[T, U]], val maxBatchSize: Int = Int.MaxValue) {

  def this(fetch: Set[T] => Future[Iterable[U]], keyExtractor: U => T) =
    this(fetch.andThen(_.map(_.map(v => (keyExtractor(v), v)).toMap)))

  def get(inputs: T*): Clump[List[U]] =
    get(inputs.toList)

  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))

  def maxBatchSize(int: Int) =
    this.copy(maxBatchSize = int)
}

object ClumpSource {

  def apply[T, U](fetch: Set[T] => Future[Iterable[U]], keyExtractor: U => T) =
    new ClumpSource(fetch, keyExtractor)

  def from[T, U](fetch: Set[T] => Future[Map[T, U]]) =
    new ClumpSource(fetch)

  def zip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    new ClumpSource(setToList.andThen(zip))
  }
}
