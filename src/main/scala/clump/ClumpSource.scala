package clump

import com.twitter.util.Future

class ClumpSource[T, U, In <: Iterable[T], Out <: Iterable[U]](val fetch: In => Future[Map[T, U]], val maxBatchSize: Int) {

  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] = {
    val fetcher = ClumpContext().fetcherFor(this)
    fetcher.append(input)
    new ClumpFetch(input, fetcher)
  }
}

object ClumpSource {
  def create[T, U, In <: Iterable[T], Out <: Iterable[U]](fetch: In => Future[Out], keyFn: U => T, maxBatchSize: Int): ClumpSource[T, U, In, Out] = {
    val resultToMap: Out => Map[T, U] = _.map(v => (keyFn(v), v)).toMap
    val futureMap: Future[Out] => Future[Map[T, U]] = _.map(resultToMap)
    val then: (In) => Future[Map[T, U]] = fetch.andThen(futureMap)
    new ClumpSource[T, U, In, Out](then, maxBatchSize)
  }
}
