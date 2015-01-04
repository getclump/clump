package clump

import com.twitter.util.Future
import com.twitter.util.Throw
import com.twitter.util.Try
import com.twitter.util.Return

sealed trait Clump[+T] {

  private val context = ClumpContext()

  def map[U](f: T => U) = flatMap(f.andThen(Clump.value(_)))

  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  def handle[B >: T](f: Throwable => B): Clump[B] = rescue[B](f.andThen(Clump.value(_)))

  def rescue[B >: T](f: Throwable => Clump[B]): Clump[B] = new ClumpRescue(this, f)

  def withFilter[B >: T](f: B => Boolean): Clump[B] = new ClumpFilter(this, f)

  def list[B](implicit ev: T <:< List[B]): Future[List[B]] = get.map(_.toList.flatten)

  def orElse[B >: T](default: => Clump[B]): Clump[B] = new ClumpOrElse(this, default)

  def optional: Clump[Option[T]] = new ClumpOptional(this)

  def apply(): Future[T] = get.map(_.get)

  def getOrElse[B >: T](default: => B): Future[B] = get.map(_.getOrElse(default))

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

  def value[T](value: T): Clump[T] =
    future(Future.value(Option(value)))

  def None[T]: Clump[T] = value(scala.None)

  def value[T](value: Option[T]): Clump[T] =
    future(Future.value(value))

  def exception[T](exception: Throwable): Clump[T] =
    future(Future.exception(exception))

  def future[T](future: Future[Option[T]]): Clump[T] =
    new ClumpFuture(future)

  def traverse[T, U](inputs: List[T])(f: T => Clump[U]) =
    collect(inputs.map(f))

  def collect[T](clumps: Clump[T]*): Clump[List[T]] =
    collect(clumps.toList)

  def collect[T](clumps: List[Clump[T]]): Clump[List[T]] =
    new ClumpCollect(clumps)

  def sourceFrom[T, U](fetch: Set[T] => Future[Map[T, U]]) =
    ClumpSource.from(fetch)

  def source[T, U](fetch: Set[T] => Future[Iterable[U]])(keyExtractor: U => T) =
    ClumpSource.apply(fetch, keyExtractor)

  def sourceZip[T, U](fetch: List[T] => Future[List[U]]) =
    ClumpSource.zip(fetch)
}

class ClumpFuture[T](val result: Future[Option[T]]) extends Clump[T] {
  val upstream = List()
  val downstream = result.liftToTry.map(_ => List())
}

class ClumpFetch[T, U](input: T, fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  val upstream = List()
  val downstream = Future.value(List())
  val result = fetcher.get(input)
}

class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  val upstream = List(a, b)
  val downstream = Future.value(List())
  val result =
    a.result.join(b.result)
      .map {
        case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
        case other                        => None
      }
}

class ClumpCollect[T](list: List[Clump[T]]) extends Clump[List[T]] {
  val upstream = list
  val downstream = Future.value(List())
  val result =
    Future
      .collect(list.map(_.result))
      .map(_.flatten.toList)
      .map(Some(_))
}

class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
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

class ClumpRescue[T](clump: Clump[T], rescue: Throwable => Clump[T]) extends Clump[T] {
  val upstream = List(clump)
  val partial =
    clump.result.liftToTry.map {
      case Return(value)    => Clump.value(value)
      case Throw(exception) => rescue(exception)
    }
  val downstream =
    partial.map(List(_))
  val result =
    partial.flatMap(_.result)
}

class ClumpFilter[T](clump: Clump[T], f: T => Boolean) extends Clump[T] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.map(_.filter(f))
}

class ClumpOrElse[T](clump: Clump[T], default: => Clump[T]) extends Clump[T] {
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

class ClumpOptional[T](clump: Clump[T]) extends Clump[Option[T]] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result = clump.result.map(Some(_))
}
