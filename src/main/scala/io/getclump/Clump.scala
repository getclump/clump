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
 * (makes 1 call to tracksService and 1 call to usersService)
 *
 * {{{
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
 * (also makes just 1 call to tracksService and 1 call to usersService)
 *
 * {{{
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

  /**
   * Define a fallback value to use in the case of specified exceptions
   */
  def handle[B >: T](f: PartialFunction[Throwable, B]): Clump[B] = new ClumpHandle(this, f)

  /**
   * Alias for [[handle]]
   */
  def recover[B >: T](f: PartialFunction[Throwable, B]): Clump[B] = handle(f)

  /**
   * Define a fallback clump to use in the case of specified exceptions
   */
  def rescue[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = new ClumpRescue(this, f)

  /**
   * Alias for [[rescue]]
   */
  def recoverWith[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = rescue(f)

  /**
   * On any exception, fallback to a default value
   */
  def fallback[B >: T](default: => B): Clump[B] = handle(PartialFunction(_ => default))

  /**
   * On any exception, fallback to a default clump
   */
  def fallbackTo[B >: T](default: => Clump[B]): Clump[B] = rescue(PartialFunction(_ => default))

  /**
   * Alias for [[filter]] used by for-comprehensions
   */
  def withFilter[B >: T](f: B => Boolean): Clump[B] = filter(f)

  /**
   * Apply a filter to this clump so that the result will only be defined if the predicate function returns true
   */
  def filter[B >: T](f: B => Boolean): Clump[B] = new ClumpFilter(this, f)

  /**
   * If this clump does not return a value then use the default instead
   */
  def orDefault[B >: T](default: => B): Clump[B] = new ClumpOrElse(this, Clump.value(default))

  /**
   * If this clump does not return a value then use the value from a default clump instead
   */
  def orElse[B >: T](default: => Clump[B]): Clump[B] = new ClumpOrElse(this, default)

  /**
   * Mark a clump as optional so that its underlying value is an option to avoid lossy joins
   */
  def optional: Clump[Option[T]] = new ClumpOptional(this)

  /**
   * A utility method for automatically unwrapping the underlying value
   * @throws NoSuchElementException if the underlying value is not defined
   */
  def apply()(implicit ec: ExecutionContext): Future[T] = get.map(_.getOrElse { throw new NoSuchElementException("Clump result was not defined") })

  /**
   * Get the result of the clump or provide a fallback value in the case where the result is not defined
   */
  def getOrElse[B >: T](default: => B)(implicit ec: ExecutionContext): Future[B] = get.map(_.getOrElse(default))

  /**
   * If the underlying value is a list, then this will return Nil instead of None when the result is not defined
   */
  def list[B >: T](implicit cbf: CanBuildFrom[Nothing, Nothing, B], ec: ExecutionContext): Future[B] =
    get.map(_.getOrElse(cbf().result()))

  /**
   * Trigger execution of a clump. The result will not be defined if any of the clump sources returned less elements
   * than requested.
   */
  def get(implicit ec: ExecutionContext): Future[Option[T]] =
    new ClumpContext()
      .flush(List(this))
      .flatMap { _ =>
        result
      }

  protected[getclump] def upstream: List[Clump[_]] = List.empty[Clump[_]]
  protected[getclump] def downstream(implicit ec: ExecutionContext): Future[Option[Clump[_]]] = Future.successful(None)
  protected[getclump] def result(implicit ec: ExecutionContext): Future[Option[T]]
}

/**
 * Clump companion object.
 */
object Clump extends Joins with Sources {

  /**
   * Create an empty clump
   */
  def empty[T]: Clump[T] = new ClumpEmpty()

  /**
   * Alias for [[value]] except that it propagates exceptions inside a clump instance
   */
  def apply[T](value: => T): Clump[T] = try { this.value(value) } catch { case NonFatal(e) => this.exception(e) }

  /**
   * The unit method: create a clump whose value has already been resolved to the input
   */
  def value[T](value: T): Clump[T] = future(Future.successful(value))

  /**
   * Alias for [[value]]
   */
  def successful[T](value: T): Clump[T] = this.value(value)

  /**
   * Create a failed clump with the given exception
   */
  def exception[T](exception: Throwable): Clump[T] = future(Future.failed(exception))

  /**
   * Alias for [[exception]]
   */
  def failed[T](exception: Throwable): Clump[T] = this.exception(exception)

  /**
   * Create a clump whose value will be the result of the inputted future
   */
  def future[T](future: Future[T]): Clump[T] = new ClumpFuture(future)

  /**
   * Transform a number of values into a single clump by first applying a function.
   *
   * Equivalent to:
   * {{{
   *   Clump.collect(clump1.map(f), clump2.map(f))
   * }}}
   */
  def traverse[T, U](inputs: T*)(f: T => Clump[U]): Clump[List[U]] = traverse(inputs.toList)(f)

  /**
   * Transform a number of clump instances into a single clump
   */
  def collect[T](clumps: Clump[T]*): Clump[List[T]] = collect(clumps.toList)

  /**
   * Alias for [[collect]]
   */
  def sequence[T](clumps: Clump[T]*): Clump[List[T]] = collect(clumps: _*)

  /**
   * Transform a collection of clump instances into a single clump by first applying a function.
   *
   * Equivalent to:
   * {{{
   *   Clump.collect(list.map(f))
   * }}}
   */
  def traverse[T, U, C[_] <: Iterable[_]](inputs: C[T])(f: T => Clump[U])(implicit cbf: CanBuildFrom[C[T], U, C[U]]): Clump[C[U]] =
    collect(inputs.toList.asInstanceOf[List[T]].map(f)).map(cbf.apply(inputs).++=(_).result())

  /**
   * Transform a collection of clump instances into a single clump
   */
  def collect[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]): Clump[C[T]] =
    new ClumpCollect(clumps)

  /**
   * Alias for [[collect]]
   */
  def sequence[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]): Clump[C[T]] =
    collect(clumps)

}

private[getclump] class ClumpEmpty extends Clump[Nothing] {
  override protected[getclump] def result(implicit ec: ExecutionContext) = Future.successful(None)
}

private[getclump] class ClumpFuture[T](future: Future[T]) extends Clump[T] {
  override protected[getclump] def downstream(implicit ec: ExecutionContext) = future.map(_ => None).recover {
    case _ => None
  }
  override protected[getclump] def result(implicit ec: ExecutionContext) = future.map(Some(_))
}

private[getclump] class ClumpFetch[T, U](input: T, val source: ClumpSource[T, U]) extends Clump[U] {
  private[this] val promise = Promise[Option[U]]

  protected[getclump] def attachTo(fetcher: ClumpFetcher[T, U])(implicit ec: ExecutionContext) =
    fetcher.get(input).onComplete(promise.complete)

  override protected[getclump] def result(implicit ec: ExecutionContext) = promise.future
}

private[getclump] class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  override protected[getclump] val upstream = List(a, b)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    a.result.zip(b.result)
      .map {
        case (Some(valueA), Some(valueB)) => Some((valueA, valueB))
        case _                            => None
      }
}

private[getclump] class ClumpCollect[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]) extends Clump[C[T]] {
  override protected[getclump] val upstream =
    clumps.toList.asInstanceOf[List[Clump[T]]]
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    Future
      .sequence(upstream.map(_.result))
      .map(_.flatten)
      .map(cbf.apply(clumps).++=(_).result())
      .map(Some(_))
}

private[getclump] class ClumpMap[T, U](clump: Clump[T], f: T => U) extends Clump[U] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.map(_.map(f))
}

private[getclump] class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
  private[this] val promise = Promise[Option[Clump[U]]]()
  private[this] def partial(implicit ec: ExecutionContext) =
    clump.result.map(_.map(f))

  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def downstream(implicit ec: ExecutionContext) =
    promise.tryCompleteWith(partial).future
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    promise.future.flatMap {
      case Some(newClump) => newClump.result
      case None           => Future.successful(None)
    }
}

private[getclump] class ClumpHandle[T](clump: Clump[T], f: PartialFunction[Throwable, T]) extends Clump[T] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.recover(f.andThen(Some(_)))
}

private[getclump] class ClumpRescue[T](clump: Clump[T], rescue: PartialFunction[Throwable, Clump[T]]) extends Clump[T] {
  private[this] val promise = Promise[Clump[T]]
  private[this] def partial(implicit ec: ExecutionContext) =
    clump.result.map(_.map(Clump.value).getOrElse(Clump.empty)).recover {
      case exception if rescue.isDefinedAt(exception) => rescue(exception)
      case exception                                  => Clump.exception(exception)
    }

  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def downstream(implicit ec: ExecutionContext) =
    promise.tryCompleteWith(partial).future.map(Some(_))
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    promise.future.flatMap(_.result)
}

private[getclump] class ClumpFilter[T](clump: Clump[T], f: T => Boolean) extends Clump[T] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    clump.result.map(_.filter(f))
}

private[getclump] class ClumpOrElse[T](clump: Clump[T], default: => Clump[T]) extends Clump[T] {
  private[this] val promise = Promise[Clump[T]]
  private[this] def partial(implicit ec: ExecutionContext) =
    clump.result.map {
      case Some(value) => Clump.value(value)
      case None        => default
    }

  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def downstream(implicit ec: ExecutionContext) =
    promise.tryCompleteWith(partial).future.map(Some(_))
  override protected[getclump] def result(implicit ec: ExecutionContext) =
    promise.future.flatMap(_.result)
}

private[getclump] class ClumpOptional[T](clump: Clump[T]) extends Clump[Option[T]] {
  override protected[getclump] val upstream = List(clump)
  override protected[getclump] def result(implicit ec: ExecutionContext) = clump.result.map(Some(_))
}
