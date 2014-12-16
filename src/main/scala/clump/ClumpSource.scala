package clump

import com.twitter.util.Future

class ClumpSource[T, U](val fetch: List[T] => Future[Map[T, U]], val maxBatchSize: Int) {

  def this(fetch: List[T] => Future[List[U]], keyFn: U => T, maxBatchSize: Int) = {
    this(fetch.andThen(_.map(_.map(v => (keyFn(v), v)).toMap)), maxBatchSize)
  }

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
}
