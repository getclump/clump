package io.getclump

import scala.collection.generic.CanBuildFrom
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
 * The trait that represents Clumps.
 *
 * See <a href="https://getclump.io/">getclump.io</a>
 *
 * In a typical microservice-powered system, it is common to find awkward wrangling code to facilitate manually bulk-fetching dependent resources.
 * Worse, this problem of batching is often accidentally overlooked, resulting in n calls to a micro-service instead of 1.
 *
 * Clump removes the need for the developer to even think about bulk-fetching, batching and retries, providing a powerful and composable interface for aggregating resources.
 *
 * An example of batching fetches using futures without Clump:
 *
 * {{{
 * // makes 1 call to tracksService and 1 call to usersService
 * tracksService.get(trackIds).flatMap { tracks =>
 *   val userIds = tracks.map(_.creator)
 *   usersService.get(userIds).map { users =>
 *     val userMap = userIds.zip(users).toMap
 *     tracks.map { track =>
 *       new EnrichedTrack(track, userMap(track.creator))
 *     }
 *   }
 * }
 * }}}
 *
 * The same composition using Clump:
 *
 * {{{
 * // also makes just 1 call to tracksService and 1 call to usersService
 * Clump.traverse(trackIds) { trackId =>
 *   for {
 *     track <- trackSource.get(trackId)
 *     user <- userSource.get(track.creator)
 *   } yield {
 *     new EnrichedTrack(track, user)
 *   }
 * }
 * }}}
 *
 * Clump exposes an API very similar to [[Future]]
 *
 * @author Flavio Brasil, Steven Heidel, William Boxhall
 */
sealed trait Clump[+T] {

  /**
   * Create a new clump by applying a function to the result of this clump
   */
  def map[U](f: T => U): Clump[U] = new ClumpMap(this, f)

  /**
   * Create a new clump by applying a function that returns a clump to the result of this clump
   */
  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  /**
   * Join this clump to another clump so that the result is a clump holding a pair of values
   */
  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  @deprecated("Use recover instead", "1.2")
  def handle[B >: T](f: PartialFunction[Throwable, B]): Clump[B] = recover(f)

  /**
   * Define a fallback value to use in the case of specified exceptions
   */
  def recover[B >: T](f: PartialFunction[Throwable, B]): Clump[B] = new ClumpRecover(this, f)

  @deprecated("Use recoverWith instead", "1.2")
  def rescue[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = recoverWith(f)

  /**
   * Define a fallback clump to use in the case of specified exceptions
   */
  def recoverWith[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = new ClumpRecoverWith(this, f)

  /**
   * On any exception, fallback to a default value
   */
  def fallback[B >: T](default: => B): Clump[B] = recover(PartialFunction(_ => default))

  /**
   * On any exception, fallback to a default clump
   */
  def fallbackTo[B >: T](default: => Clump[B]): Clump[B] = recoverWith(PartialFunction(_ => default))

  /**
   * Alias for [[filter]] used by for-comprehensions
   */
  def withFilter[B >: T](f: B => Boolean): Clump[B] = filter(f)

  /**
   * Apply a filter to this clump so that the result will only be defined if the predicate function returns true
   */
  def filter[B >: T](f: B => Boolean): Clump[B] =
    map { r => if (f(r)) r else throw ClumpNoSuchElementException("Clump.filter predicate is not satisfied") }

  @deprecated("Use .optional.map(_.getOrElse(default)) instead", "1.2")
  def orDefault[B >: T](default: => B): Clump[B] =
    optional.map(_.getOrElse(default))

  @deprecated("Use .optional.flatMap(_.map(Clump.successful).getOrElse(default)) instead", "1.2")
  def orElse[B >: T](default: => Clump[B]): Clump[B] =
    optional.flatMap(_.map(Clump.successful).getOrElse(default))

  /**
   * Mark a clump as optional so that its underlying value is an option to avoid lossy joins
   */
  def optional: Clump[Option[T]] = new ClumpOptional(this)

  /**
   * A utility method for automatically unwrapping the underlying value
   * @throws ClumpNoSuchElementException if the underlying value is not defined
   */
  def apply()(implicit ec: ExecutionContext): Future[T] =
    new ClumpContext().flush(List(this)).flatMap { _ => result }

  @deprecated("Use .optional.apply().map(_.getOrElse(default)) instead", "1.2")
  def getOrElse[B >: T](default: => B)(implicit ec: ExecutionContext): Future[B] =
    optional.apply().map(_.getOrElse(default))

  @deprecated("Use .optional.apply().map(_.getOrElse(Nil)) instead", "1.2")
  def list[B >: T](implicit cbf: CanBuildFrom[Nothing, Nothing, B], ec: ExecutionContext): Future[B] =
    optional.apply().map(_.getOrElse(cbf().result()))

  @deprecated("Use .optional.apply() instead", "1.2")
  def get(implicit ec: ExecutionContext): Future[Option[T]] = optional.apply()

  protected[getclump] def upstream: List[Clump[_]] = List.empty[Clump[_]]
  protected[getclump] def downstream(implicit ec: ExecutionContext): Future[Option[Clump[_]]] = Future.successful(None)
  protected[getclump] def result(implicit ec: ExecutionContext): Future[T]
}

/**
 * Clump companion object.
 */
object Clump extends Joins with Sources {

  /**
   * Create an empty clump
   */
  def empty[T]: Clump[T] = failed(ClumpNoSuchElementException("Clump.empty"))

  @deprecated("Use Clump.successful or Clump.failed instead", "1.2")
  def apply[T](value: => T): Clump[T] = try { successful(value) } catch { case NonFatal(e) => failed(e) }

  @deprecated("Use Clump.successful instead", "1.2")
  def value[T](value: T): Clump[T] = successful(value)

  /**
   * The unit method: create a clump whose value has already been resolved to the input
   */
  def successful[T](value: T): Clump[T] = future(Future.successful(value))

  @deprecated("Use Clump.failed instead", "1.2")
  def exception[T](exception: Throwable): Clump[T] = failed(exception)

  /**
   * Create a failed clump with the given exception
   */
  def failed[T](exception: Throwable): Clump[T] = future(Future.failed(exception))

  /**
   * Create a clump whose value will be the result of the inputted future
   */
  def future[T](future: Future[T]): Clump[T] = new ClumpFuture(future)

  @deprecated("Use Future.traverse(List()) instead", "1.2")
  def traverse[T, U](inputs: T*)(f: T => Clump[U]): Clump[List[U]] = traverse(inputs.toList)(f)

  @deprecated("Use Future.sequence(List()) instead", "1.2")
  def collect[T](clumps: Clump[T]*): Clump[List[T]] = sequence(clumps.toList)

  @deprecated("Use Future.sequence(List()) instead", "1.2")
  def sequence[T](clumps: Clump[T]*): Clump[List[T]] = sequence(clumps.toList)

  /**
   * Transform a collection of clump instances into a single clump by first applying a function.
   *
   * Equivalent to:
   * {{{
   *   Clump.sequence(list.map(f))
   * }}}
   */
  def traverse[T, U, C[_] <: Iterable[_]](inputs: C[T])(f: T => Clump[U])(implicit cbf: CanBuildFrom[C[T], U, C[U]]): Clump[C[U]] =
    sequence(inputs.toList.asInstanceOf[List[T]].map(f)).map(cbf.apply(inputs).++=(_).result())

  @deprecated("Use Clump.sequence instead", "1.2")
  def collect[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]): Clump[C[T]] =
    sequence(clumps)

  /**
   * Transform a collection of clump instances into a single clump
   */
  def sequence[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]): Clump[C[T]] =
    new ClumpSequence(clumps)

}

private[getclump] class ClumpFuture[T](future: Future[T]) extends Clump[T] {
  override protected[getclump] def result(implicit ec: ExecutionContext) = future
}

private[getclump] class ClumpFetch[T, U](input: T, val source: ClumpSource[T, U]) extends Clump[U] {
  private[this] val promise = Promise[U]

  protected[getclump] def attachTo(fetcher: ClumpFetcher[T, U])(implicit ec: ExecutionContext) =
    fetcher.get(input).onComplete(promise.complete)

  override protected[getclump] def result(implicit ec: ExecutionContext) = promise.future
}

private[getclump] class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  override protected[getclump] val upstream = List(a, b)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    a.result.zip(b.result)
}

private[getclump] class ClumpSequence[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]) extends Clump[C[T]] {
  override protected[getclump] val upstream =
    clumps.toList.asInstanceOf[List[Clump[T]]]
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    Future
      .sequence(upstream.map(_.result.map(Some(_)).recover {
        // TODO: Remove in version 2.0
        case _: ClumpNoSuchElementException => None
      }))
      .map(_.flatten)
      .map(cbf.apply(clumps).++=(_).result())
}

private[getclump] class ClumpMap[T, U](clump: Clump[T], f: T => U) extends Clump[U] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.map(f)
}

private[getclump] class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
  private[this] val promise = Promise[Clump[U]]()
  private[this] def partial(implicit ec: ExecutionContext) =
    clump.result.map(f)

  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def downstream(implicit ec: ExecutionContext) =
    promise.tryCompleteWith(partial).future.map(Some(_)).recover {
      case _: ClumpNoSuchElementException => None
    }
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    promise.future.flatMap(_.result)
}

private[getclump] class ClumpRecover[T](clump: Clump[T], f: PartialFunction[Throwable, T]) extends Clump[T] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.recover {
      // TODO: Remove in version 2.0
      case exception if f.isDefinedAt(exception) && !exception.isInstanceOf[ClumpNoSuchElementException] => f(exception)
    }
}

private[getclump] class ClumpRecoverWith[T](clump: Clump[T], f: PartialFunction[Throwable, Clump[T]]) extends Clump[T] {
  private[this] val promise = Promise[Clump[T]]
  private[this] def partial(implicit ec: ExecutionContext) =
    clump.result.map(Clump.successful).recover {
      // TODO: Remove in version 2.0
      case exception: ClumpNoSuchElementException => Clump.failed(exception)
      case exception if f.isDefinedAt(exception)  => f(exception)
      case exception                              => Clump.failed(exception)
    }

  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def downstream(implicit ec: ExecutionContext) =
    promise.tryCompleteWith(partial).future.map(Some(_)).recover {
      case _: ClumpNoSuchElementException => None
    }
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    promise.future.flatMap(_.result)
}

// TODO: Once the matchers in recover / recoverWith for ClumpNoSuchElementException are removed this can be inlined
private[getclump] class ClumpOptional[T](clump: Clump[T]) extends Clump[Option[T]] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.map(Some(_)).recover {
      case _: ClumpNoSuchElementException => None
    }
}