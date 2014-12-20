package clump

import com.twitter.util.Future
import com.twitter.util.Throw
import com.twitter.util.Try

trait Clump[+T] {

  private val forceContextInit = ClumpContext()

  def map[U](f: T => U) = flatMap(f.andThen(Clump.value(_)))

  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  def handle[B >: T](f: Throwable => B): Clump[B] = rescue[B](f.andThen(Clump.value(_)))

  def rescue[B >: T](f: Throwable => Clump[B]): Clump[B] = new ClumpRescue(this, f)

  def withFilter[B >: T](f: B => Boolean): Clump[B] = new ClumpFilter(this, f)

  def run = Future.Unit.flatMap(_ => result)

  protected def result: Future[Option[T]]
}

object Clump {

  def value[T](value: T): Clump[T] =
    future(Future.value(Option(value)))

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

  def sourceFrom[T, U](fetch: Set[T] => Future[Map[T, U]], maxBatchSize: Int = Int.MaxValue) =
    new ClumpSource(fetch, maxBatchSize)

  def source[T, U](fetch: Set[T] => Future[Iterable[U]], maxBatchSize: Int = Int.MaxValue)(keyFn: U => T) =
    new ClumpSource(fetch, keyFn, maxBatchSize)
}

class ClumpFuture[T](future: Future[Option[T]]) extends Clump[T] {
  lazy val result = future
}

class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  lazy val result =
    a.run.join(b.run).map {
      case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
      case other                        => None
    }
}

class ClumpCollect[T](list: List[Clump[T]]) extends Clump[List[T]] {
  lazy val result =
    Future
      .collect(list.map(_.run))
      .map(_.flatten.toList)
      .map(Some(_))
}

class ClumpFetch[T, U](input: T, fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  lazy val result = fetcher.run(input)
}

class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
  lazy val result =
    clump.run.flatMap {
      case Some(value) => f(value).run
      case None        => Future.None
    }
}

class ClumpRescue[T](clump: Clump[T], rescue: Throwable => Clump[T]) extends Clump[T] {
  lazy val result =
    clump.run.rescue {
      case exception => rescue(exception).run
    }
}

class ClumpFilter[T](clump: Clump[T], f: T => Boolean) extends Clump[T] {
  lazy val result =
    clump.run.map(_.filter(f))
}
