package io.getclump

import scala.collection.generic.CanBuildFrom


class ClumpSource[T, U] private[getclump] (val fetch: List[T] => Future[Map[T, U]],
                                           val maxBatchSize: Int = 100,
                                           val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  /**
   * Get a list of values from a clump source
   */
  def get[C[_] <: Iterable[_]](inputs: C[T])(implicit cbf: CanBuildFrom[Nothing, U, C[U]]): Clump[C[U]] = {
    val listInputs = inputs.toList.asInstanceOf[List[T]]
    val clumpList = Clump.collect(listInputs.map(get))
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
