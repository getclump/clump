package clump

import com.twitter.util.Future
import scala.collection.generic.CanBuildFrom

class ClumpSource[T, U] private (val fetch: Set[T] => Future[Map[T, U]], val maxBatchSize: Int = Int.MaxValue) {

  def list(inputs: T*): Clump[List[U]] = list(inputs.toList)

  def list(inputs: List[T]): Clump[List[U]] = Clump.collect(inputs.map(apply))

  def get(input: T): Clump[Option[U]] = apply(input).optional

  def getOrElse(input: T,  default: => U): Clump[U] = apply(input).orElse(Clump.value(default))

  def apply(input: T): Clump[U] = new ClumpFetch(input, ClumpContext().fetcherFor(this))

  def maxBatchSize(size: Int): ClumpSource[T, U] = new ClumpSource(fetch, size)
}

object ClumpSource {

  def apply[T, U, C](fetch: C => Future[Iterable[U]])(keyExtractor: U => T)(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    new ClumpSource(extractKeys(adaptInput(fetch), keyExtractor))

  def from[T, U, C](fetch: C => Future[Map[T, U]])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    new ClumpSource(adaptInput(fetch))

  def zip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] = {
    new ClumpSource(zipped(fetch))
  }

  private def zipped[T, U](fetch: List[T] => Future[List[U]]) = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    setToList.andThen(zip)
  }

  private def extractKeys[T, U](fetch: Set[T] => Future[Iterable[U]], keyExtractor: U => T) =
    fetch.andThen(_.map(resultsToKeys(keyExtractor, _)))

  private def resultsToKeys[U, T](keyExtractor: (U) => T, results: Iterable[U]) =
    results.map(v => (keyExtractor(v), v)).toMap

  private def adaptInput[T, C, R](fetch: C => Future[R])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    (c: Set[T]) => fetch(cbf.apply().++=(c).result())
}
