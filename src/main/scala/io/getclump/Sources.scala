package io.getclump

import com.twitter.util.Future

import scala.collection.generic.CanBuildFrom

trait Sources extends Tuples {

  def source[KS, V, K](fetch: KS => Future[Iterable[V]])(keyExtractor: V => K)
                     (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), extractKeys(adaptInput(fetch), keyExtractor))

  def source[A, KS, V, K](fetch: (A, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                         (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetch(normalize1, denormalize1[A, K], fetch1(fetch), keyExtractor))

  def source[A, B, KS, V, K](fetch: (A, B, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                             (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetch(normalize2, denormalize2[A, B, K], fetch2(fetch), keyExtractor))

  def source[A, B, C, KS, V, K](fetch: (A, B, C, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                                 (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetch(normalize3, denormalize3[A, B, C, K], fetch3(fetch), keyExtractor))

  def source[A, B, C, D, KS, V, K](fetch: (A, B, C, D, KS) => Future[Iterable[V]])(keyExtractor: V => K)
                                     (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, D, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetch(normalize4, denormalize4[A, B, C, D, K], fetch4(fetch), keyExtractor))

  def sourceFrom[KS, K, V](fetch: KS => Future[Iterable[(K, V)]])
                         (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), adaptOutput(adaptInput(fetch)))

  def sourceFrom[A, KS, K, V](fetch: (A, KS) => Future[Iterable[(K, V)]])
                             (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetchFrom(normalize1, denormalize1[A, K], fetch1(fetch)))

  def sourceFrom[A, B, KS, K, V](fetch: (A, B, KS) => Future[Iterable[(K, V)]])
                                 (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetchFrom(normalize2, denormalize2[A, B, K], fetch2(fetch)))

  def sourceFrom[A, B, C, KS, K, V](fetch: (A, B, C, KS) => Future[Iterable[(K, V)]])
                                     (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetchFrom(normalize3, denormalize3[A, B, C, K], fetch3(fetch)))

  def sourceFrom[A, B, C, D, KS, K, V](fetch: (A, B, C, D, KS) => Future[Iterable[(K, V)]])
                                         (implicit cbf: CanBuildFrom[Nothing, K, KS]): ClumpSource[(A, B, C, D, K), V] =
    new ClumpSource(FunctionIdentity(fetch), parametrizeFetchFrom(normalize4, denormalize4[A, B, C, D, K], fetch4(fetch)))

  def sourceZip[K, V](fetch: List[K] => Future[List[V]]): ClumpSource[K, V] =
    new ClumpSource(FunctionIdentity(fetch), zipped(fetch))

  private def parametrizeFetchFrom[I, P, O, T, C](normalize: I => (P, T), denormalize: (P, T) => I, fetch: (P, C) => Future[Iterable[(T, O)]])
                                             (implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
    (inputs: Set[I]) => {
      val futures =
        inputs.map(normalize).groupBy(_._1).map {
          case (params, values) =>
            fetch(params, cbf.apply().++=(values.map(_._2)).result).map {
              _.map(kv => denormalize(params, kv._1) -> kv._2)
            }
        }.toSeq
      Future.collect(futures).map(_.reduce(_ ++ _).toMap)
    }

  private def parametrizeFetch[I, P, O, T, C](normalize: I => (P, T), denormalize: (P, T) => I, fetch: (P, C) => Future[Iterable[O]], extractKey: O => T)
                                              (implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
    parametrizeFetchFrom[I, P, O, T, C](normalize, denormalize, fetch = (params: P, coll: C) => fetch(params, coll).map(_.map(v => extractKey(v) -> v).toMap))

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
