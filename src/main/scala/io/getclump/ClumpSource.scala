package io.getclump

import com.twitter.util.Future
import scala.collection.generic.CanBuildFrom

class ClumpSourceWithParam[T, U, P](val functionIdentity: FunctionIdentity,
                                    val fetch: ((P, Set[T])) => Future[Map[T, U]],
                                    val maxBatchSize: Int = Int.MaxValue,
                                    val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  def get(param: P, inputs: T*): Clump[List[U]] =
    get(param, inputs.toList)

  def get(param: P, inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get(param, _)))

  def get(param: P, input: T): Clump[U] = {
    val parameterized: Set[T] => Future[Map[T, U]] = fetch(param, _)
    val clumpSource: ClumpSource[T, U] = new ClumpSource[T, U](functionIdentity, parameterized, maxBatchSize, _maxRetries)
    new ClumpFetch(input, ClumpContext().fetcherFor(clumpSource))
  }

  def maxBatchSize(size: Int): ClumpSourceWithParam[T, U, P] =
    new ClumpSourceWithParam(functionIdentity, fetch, size, _maxRetries)

  def maxRetries(retries: PartialFunction[Throwable, Int]): ClumpSourceWithParam[T, U, P] =
    new ClumpSourceWithParam(functionIdentity, fetch, maxBatchSize, retries)
}

private[getclump] object ClumpSourceWithParam {

  def apply[T, U, C, P](fetch: (P, C) => Future[Iterable[U]])(keyExtractor: U => T)(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSourceWithParam[T, U, P] =
    new ClumpSourceWithParam(FunctionIdentity(fetch), extractKeys(adaptInput(fetch), keyExtractor))

  def from[T, U, C, P](fetch: (P, C) => Future[Iterable[(T, U)]])(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSourceWithParam[T, U, P] =
    new ClumpSourceWithParam(FunctionIdentity(fetch), adaptOutput(adaptInput(fetch)))

  def zip[T, U, P](fetch: (P, List[T]) => Future[List[U]]): ClumpSourceWithParam[T, U, P] = {
    new ClumpSourceWithParam(FunctionIdentity(fetch), zipped(fetch))
  }

  private[this] def zipped[T, U, P](fetch: (P, List[T]) => Future[List[U]]) = {
    val zip: ((P, List[T])) => Future[Map[T, U]] = { case ((param, inputs)) =>
      fetch(param, inputs).map(inputs.zip(_).toMap)
    }
    val setToList: (P, Set[T]) => (P, List[T]) = { (param, input) => (param, input.toList) }
    setToList.tupled.andThen(zip)
  }

  private[this] def extractKeys[T, U, P](fetch: (P, Set[T]) => Future[Iterable[U]], keyExtractor: U => T) = {
    val map: (Future[Iterable[U]]) => Future[Map[T, U]] = _.map(resultsToKeys(keyExtractor, _))
    fetch.tupled.andThen(map)
  }

  private[this] def resultsToKeys[U, T](keyExtractor: (U) => T, results: Iterable[U]) =
    results.map(v => (keyExtractor(v), v)).toMap

  private[this] def adaptInput[T, C, R, P](fetch: (P, C) => Future[R])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    (p: P, c: Set[T]) => fetch(p, cbf.apply().++=(c).result())

  private[this] def adaptOutput[T, U, C, P](fetch: (P, C) => Future[Iterable[(T, U)]]) =
    fetch.tupled.andThen(_.map(_.toMap))
}

class ClumpSource[T, U](val functionIdentity: FunctionIdentity,
                        val fetch: Set[T] => Future[Map[T, U]],
                        val maxBatchSize: Int = Int.MaxValue,
                        val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  def get(inputs: T*): Clump[List[U]] =
    get(inputs.toList)

  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))

  def maxBatchSize(size: Int): ClumpSource[T, U] =
    new ClumpSource(functionIdentity, fetch, size, _maxRetries)

  def maxRetries(retries: PartialFunction[Throwable, Int]): ClumpSource[T, U] =
    new ClumpSource(functionIdentity, fetch, maxBatchSize, retries)
}

private[getclump] object ClumpSource {

  def apply[T, U, C](fetch: C => Future[Iterable[U]])(keyExtractor: U => T)(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSource[T, U] =
    new ClumpSource(FunctionIdentity(fetch), extractKeys(adaptInput(fetch), keyExtractor))

  def from[T, U, C](fetch: C => Future[Iterable[(T, U)]])(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSource[T, U] =
    new ClumpSource(FunctionIdentity(fetch), adaptOutput(adaptInput(fetch)))

  def zip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] = {
    new ClumpSource(FunctionIdentity(fetch), zipped(fetch))
  }

  private[this] def zipped[T, U](fetch: List[T] => Future[List[U]]) = {
    val zip: List[T] => Future[Map[T, U]] = { inputs =>
      fetch(inputs).map(inputs.zip(_).toMap)
    }
    val setToList: Set[T] => List[T] = _.toList
    setToList.andThen(zip)
  }

  private[this] def extractKeys[T, U](fetch: Set[T] => Future[Iterable[U]], keyExtractor: U => T) =
    fetch.andThen(_.map(resultsToKeys(keyExtractor, _)))

  private[this] def resultsToKeys[U, T](keyExtractor: (U) => T, results: Iterable[U]) =
    results.map(v => (keyExtractor(v), v)).toMap

  private[this] def adaptInput[T, C, R](fetch: C => Future[R])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    (c: Set[T]) => fetch(cbf.apply().++=(c).result())

  private[this] def adaptOutput[T, U, C](fetch: C => Future[Iterable[(T, U)]]) =
    fetch.andThen(_.map(_.toMap))
}
