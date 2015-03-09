package io.getclump

import scala.annotation.implicitNotFound
import scala.annotation.migration
import scala.collection.generic.CanBuildFrom

import com.twitter.util.Future

trait ClumpSourceFactory {

  trait PartiallyDefinedClumpSource

  def source[C] = new PartiallyDefinedClumpSource {

    def apply[I, O](fetch: C => Future[Iterable[O]])(keyExtractor: O => I)(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[I, O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        extractKeys(adaptInput(fetch), keyExtractor))

    def apply[I, O, P1](fetch: (P1, C) => Future[Iterable[O]])(keyExtractor: O => I)(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch2(
          normalize = (inputs: (P1, I)) => ((inputs._1), inputs._2),
          denormalize = (p1: P1, i: I) => (p1, i),
          fetch = (params: (P1), values: C) => fetch(params, values),
          extractKey = keyExtractor))

    def apply[I, O, P1, P2](fetch: (P1, P2, C) => Future[Iterable[O]])(keyExtractor: O => I)(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch2(
          normalize = (inputs: (P1, P2, I)) => ((inputs._1, inputs._2), inputs._3),
          denormalize = (params: (P1, P2), i: I) => (params._1, params._2, i),
          fetch = (params: (P1, P2), values: C) => fetch(params._1, params._2, values),
          extractKey = keyExtractor))

    def apply[I, O, P1, P2, P3](fetch: (P1, P2, P3, C) => Future[Iterable[O]])(keyExtractor: O => I)(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, P3, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch2(
          normalize = (inputs: (P1, P2, P3, I)) => ((inputs._1, inputs._2, inputs._3), inputs._4),
          denormalize = (params: (P1, P2, P3), i: I) => (params._1, params._2, params._3, i),
          fetch = (params: (P1, P2, P3), values: C) => fetch(params._1, params._2, params._3, values),
          extractKey = keyExtractor))

    def apply[I, O, P1, P2, P3, P4](fetch: (P1, P2, P3, P4, C) => Future[Iterable[O]])(keyExtractor: O => I)(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, P3, P4, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch2(
          normalize = (inputs: (P1, P2, P3, P4, I)) => ((inputs._1, inputs._2, inputs._3, inputs._4), inputs._5),
          denormalize = (params: (P1, P2, P3, P4), i: I) => (params._1, params._2, params._3, params._4, i),
          fetch = (params: (P1, P2, P3, P4), values: C) => fetch(params._1, params._2, params._3, params._4, values),
          extractKey = keyExtractor))
  }

  def sourceFrom[C] = new PartiallyDefinedClumpSource {

    def apply[I, O](fetch: C => Future[Iterable[(I, O)]])(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[I, O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        adaptOutput(adaptInput(fetch)))

    def apply[I, O, P1](fetch: (P1, C) => Future[Iterable[(I, O)]])(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch(
          normalize = (inputs: (P1, I)) => ((inputs._1), inputs._2),
          denormalize = (p1: P1, i: I) => (p1, i),
          fetch = (params: (P1), values: C) => fetch(params, values)))

    def apply[I, O, P1, P2](fetch: (P1, P2, C) => Future[Iterable[(I, O)]])(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch(
          normalize = (inputs: (P1, P2, I)) => ((inputs._1, inputs._2), inputs._3),
          denormalize = (params: (P1, P2), i: I) => (params._1, params._2, i),
          fetch = (params: (P1, P2), values: C) => fetch(params._1, params._2, values)))

    def apply[I, O, P1, P2, P3](fetch: (P1, P2, P3, C) => Future[Iterable[(I, O)]])(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, P3, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch(
          normalize = (inputs: (P1, P2, P3, I)) => ((inputs._1, inputs._2, inputs._3), inputs._4),
          denormalize = (params: (P1, P2, P3), i: I) => (params._1, params._2, params._3, i),
          fetch = (params: (P1, P2, P3), values: C) => fetch(params._1, params._2, params._3, values)))

    def apply[I, O, P1, P2, P3, P4](fetch: (P1, P2, P3, P4, C) => Future[Iterable[(I, O)]])(
      implicit cbf: CanBuildFrom[Nothing, I, C]): ClumpSource[(P1, P2, P3, P4, I), O] =
      new ClumpSource(
        FunctionIdentity(fetch),
        parametrizeFetch(
          normalize = (inputs: (P1, P2, P3, P4, I)) => ((inputs._1, inputs._2, inputs._3, inputs._4), inputs._5),
          denormalize = (params: (P1, P2, P3, P4), i: I) => (params._1, params._2, params._3, params._4, i),
          fetch = (params: (P1, P2, P3, P4), values: C) => fetch(params._1, params._2, params._3, params._4, values)))
  }

  def sourceZip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] =
    new ClumpSource(FunctionIdentity(fetch), zipped(fetch))

  private def parametrizeFetch[I, P, O, T, C](
    normalize: I => (P, T),
    denormalize: (P, T) => I,
    fetch: (P, C) => Future[Iterable[(T, O)]])(
      implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
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

  private def parametrizeFetch2[I, P, O, T, C](
    normalize: I => (P, T),
    denormalize: (P, T) => I,
    fetch: (P, C) => Future[Iterable[O]],
    extractKey: O => T)(
      implicit cbf: CanBuildFrom[Nothing, T, C]): Set[I] => Future[Map[I, O]] =
    parametrizeFetch[I, P, O, T, C](
      normalize = normalize,
      denormalize = denormalize,
      fetch = (params: P, coll: C) =>
        fetch(params, coll).map(_.map(v => extractKey(v) -> v).toMap))

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
