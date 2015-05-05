package io.getclump


class ClumpSource[T, U] private[getclump] (val fetch: Set[T] => Future[Map[T, U]],
                                           val maxBatchSize: Int = Int.MaxValue,
                                           val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  /**
   * Get a list of values from a clump source
   */
  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  /**
   * Get a single value from a clump source
   */
  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))

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
