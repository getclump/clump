package io.getclump

import com.twitter.util.Future
import scala.collection.generic.CanBuildFrom

class ClumpSource[T, U] private[getclump] (val functionIdentity: FunctionIdentity,
                                              val fetch: Set[T] => Future[Map[T, U]],
                                              val maxBatchSize: Int = Int.MaxValue,
                                              val _maxRetries: PartialFunction[Throwable, Int] = PartialFunction.empty) {

  def get(inputs: List[T]): Clump[List[U]] =
    Clump.collect(inputs.map(get))

  def get(input: T): Clump[U] =
    new ClumpFetch(input, ClumpContext().fetcherFor(this))

  def maxBatchSize(size: Int): ClumpSource[T, U] =
    new ClumpSource(functionIdentity, fetch, size, _maxRetries)

  def maxRetries(retries: PartialFunction[Throwable, Int]): ClumpSource[T, U] =
    new ClumpSource(functionIdentity, fetch, maxBatchSize, retries)
}
