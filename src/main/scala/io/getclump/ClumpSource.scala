package io.getclump

import scala.collection.generic.CanBuildFrom


/**
 * Sources represent the remote systems' batched interfaces.
 *
 * There are multiple create a ClumpSource:
 * - [[Clump.source]] with a function that returns a Map
 * - [[Clump.source]] with a function that returns a collection and another function that maps values to keys
 * - [[Clump.sourceTry]] with a function that returns a Map where each entry can be Success or Failure
 * - [[Clump.sourceZip]] with a function that returns a List with the same order and size as the input list
 * - [[Clump.sourceSingle]] with a function that accepts a single id and returns a single resource
 *
 * Once created, a Clump can be retrieved from an id using the [[get]] method.
 *
 * See [[Clump]]
 */
class ClumpSource[T, U] private[getclump] (val fetch: List[T] => Future[Map[T, Try[U]]],
                                           val maxBatchSize: Int = 100,
                                           val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  /**
   * Get a list of values from a clump source
   */
  def get[C[_] <: Iterable[_]](inputs: C[T])(implicit cbf: CanBuildFrom[Nothing, U, C[U]]): Clump[C[U]] = {
    val listInputs = inputs.toList.asInstanceOf[List[T]]
    val clumpList = Clump.sequence(listInputs.map(get))
    clumpList.map(list => cbf.apply().++=(list).result())
  }

  /**
   * Get a single value from a clump source
   */
  def get(input: T): Clump[U] =
    new ClumpFetch(input, this)

  /**
   * Set the maximum batch size for fetching values from this clump source
   */
  def maxBatchSize(size: Int): ClumpSource[T, U] =
    new ClumpSource(fetch, size, _maxRetries)

  /**
   * Set the maximum number of retries for fetching values from this clump source in case of failure.
   * Different number of retries can be set for different types of exceptions.
   */
  def maxRetries(retries: PartialFunction[Throwable, Int]): ClumpSource[T, U] =
    new ClumpSource(fetch, maxBatchSize, retries)
}
