package clump

import com.twitter.util.Future

class ClumpSource[T, U](val fetch: Set[T] => Future[Map[T, U]], val maxBatchSize: Int) {

  def this(fetch: Set[T] => Future[Iterable[U]], keyFn: U => T, maxBatchSize: Int) = {
    this(fetch.andThen(_.map(_.map(v => (keyFn(v), v)).toMap)), maxBatchSize)
  }

  def get(inputs: T*): Clump[List[U]] =
    get(inputs.toList)

  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))
}

object ClumpSource {
  def zip[T, U](fetch: List[T] => Future[List[U]], maxBatchSize: Int): ClumpSource[T, U] = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    new ClumpSource(setToList.andThen(zip), maxBatchSize)
  }
}
