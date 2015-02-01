package clump

import scala.collection.generic.CanBuildFrom

import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw

sealed trait Clump[+T] {

  private[this] val context = ClumpContext()

  def map[U](f: T => U): Clump[U] = new ClumpMap(this, f)

  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  def handle[B >: T](f: PartialFunction[Throwable, Option[B]]): Clump[B] = new ClumpHandle(this, f)

  def rescue[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = new ClumpRescue(this, f)

  def filter[B >: T](f: B => Boolean): Clump[B] = new ClumpFilter(this, f)

  def orElse[B >: T](default: => Clump[B]): Clump[B] = new ClumpOrElse(this, default)

  def optional: Clump[Option[T]] = new ClumpOptional(this)

  def apply(): Future[T] = get.map(_.get)

  def getOrElse[B >: T](default: => B): Future[B] = get.map(_.getOrElse(default))

  def list[B](implicit ev: T <:< List[B]): Future[List[B]] = get.map(_.toList.flatten)

  def get: Future[Option[T]] =
    context
      .flush(List(this))
      .flatMap { _ =>
        result
      }

  protected[clump] def upstream: List[Clump[_]]
  protected[clump] def downstream: Future[List[Clump[_]]]
  protected[clump] def result: Future[Option[T]]
}

object Clump {

  def empty[T]: Clump[T] = value(scala.None)

  def value[T](value: T): Clump[T] = future(Future.value(Option(value)))

  def value[T](value: Option[T]): Clump[T] = future(Future.value(value))

  def exception[T](exception: Throwable): Clump[T] = future(Future.exception(exception))

  def future[T](future: Future[Option[T]]): Clump[T] = new ClumpFuture(future)

  def collect[T](clumps: Clump[T]*): Clump[List[T]] = collect(clumps.toList)

  def traverse[T, U](inputs: List[T])(f: T => Clump[U]) = collect(inputs.map(f))

  def collect[T](clumps: List[Clump[T]]): Clump[List[T]] = new ClumpCollect(clumps)

  def sourceFrom[T, U, C](fetch: C => Future[Map[T, U]])(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    ClumpSource.from(fetch)

  def source[T, U, C](fetch: C => Future[Iterable[U]])(keyExtractor: U => T)(implicit cbf: CanBuildFrom[Nothing, T, C]) =
    ClumpSource(fetch)(keyExtractor)

  def sourceZip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] =
    ClumpSource.zip(fetch)
}

private[clump] class ClumpFuture[T](val result: Future[Option[T]]) extends Clump[T] {
  val upstream = List()
  val downstream = result.liftToTry.map(_ => List())
}

private[clump] class ClumpFetch[T, U](input: T, val fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  val upstream = List()
  val downstream = Future.value(List())
  val result = fetcher.get(input)
}

private[clump] class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  val upstream = List(a, b)
  val downstream = Future.value(List())
  val result =
    a.result.join(b.result)
      .map {
        case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
        case _                            => None
      }
}

private[clump] class ClumpCollect[T](list: List[Clump[T]]) extends Clump[List[T]] {
  val upstream = list
  val downstream = Future.value(List())
  val result =
    Future
      .collect(list.map(_.result))
      .map(_.flatten.toList)
      .map(Some(_))
}

private[clump] class ClumpMap[T, U](clump: Clump[T], f: T => U) extends Clump[U] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.map(_.map(f))
}

private[clump] class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
  val upstream = List(clump)
  val partial =
    clump.result.map(_.map(f))
  val downstream =
    partial.map(_.toList)
  val result =
    partial.flatMap {
      case Some(clump) => clump.result
      case None        => Future.None
    }
}

private[clump] class ClumpHandle[T](clump: Clump[T], f: PartialFunction[Throwable, Option[T]]) extends Clump[T] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.handle(f)
}

private[clump] class ClumpRescue[T](clump: Clump[T], rescue: PartialFunction[Throwable, Clump[T]]) extends Clump[T] {
  val upstream = List(clump)
  val partial =
    clump.result.liftToTry.map {
      case Throw(exception) if (rescue.isDefinedAt(exception)) => rescue(exception)
      case Throw(exception)                                    => Clump.exception(exception)
      case Return(value)                                       => Clump.value(value)
    }
  val downstream =
    partial.map(List(_))
  val result =
    partial.flatMap(_.result)
}

private[clump] class ClumpFilter[T](clump: Clump[T], f: T => Boolean) extends Clump[T] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.map(_.filter(f))
}

private[clump] class ClumpOrElse[T](clump: Clump[T], default: => Clump[T]) extends Clump[T] {
  val upstream = List(clump)
  val partial =
    clump.result.map {
      case Some(value) => Clump.value(value)
      case None        => default
    }
  val downstream =
    partial.map(List(_))
  val result =
    partial.flatMap(_.result)
}

private[clump] class ClumpOptional[T](clump: Clump[T]) extends Clump[Option[T]] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result = clump.result.map(Some(_))
}
