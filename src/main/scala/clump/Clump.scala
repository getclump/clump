package clump

import com.twitter.util.Future
import com.twitter.util.Throw
import com.twitter.util.Try

trait Clump[T] {

  private val forceContextInit = ClumpContext()

  def map[U](f: T => U) = flatMap(f.andThen(Clump.value(_)))

  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  def handle(f: Throwable => T): Clump[T] = rescue(f.andThen(Clump.value(_)))

  def rescue(f: Throwable => Clump[T]): Clump[T] = new ClumpRescue(this, f)

  def run = Future.Unit.flatMap(_ => result)

  protected def result: Future[T]
}

object Clump {

  def value[T](value: T): Clump[T] =
    future(Future.value(value))

  def exception[T](exception: Throwable): Clump[T] =
    future(Future.exception(exception))

  def future[T](future: Future[T]): Clump[T] =
    new ClumpFuture(future)

  def traverse[T, U](inputs: List[T])(f: T => Clump[U]) =
    collect(inputs.map(f))

  def collect[T](clumps: Clump[T]*): Clump[List[T]] =
    collect(clumps.toList)

  def collect[T](clumps: List[Clump[T]]): Clump[List[T]] =
    new ClumpCollect(clumps)

  def collect[T](option: Option[Clump[T]]): Clump[Option[T]] = option match {
    case None => Clump.value(None)
    case Some(clump) => clump.map(Some(_))
  }

  def sourceFrom[T, U](fetch: Set[T] => Future[Map[T, U]], maxBatchSize: Int = Int.MaxValue) =
    new ClumpSource(fetch, maxBatchSize)

  def source[T, U](fetch: Set[T] => Future[Iterable[U]], maxBatchSize: Int = Int.MaxValue)(keyFn: U => T) =
    new ClumpSource(fetch, keyFn, maxBatchSize)
}

class ClumpFuture[T](future: Future[T]) extends Clump[T] {
  lazy val result = future
}

class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  lazy val result = a.run.join(b.run)
}

class ClumpCollect[T](list: List[Clump[T]]) extends Clump[List[T]] {
  lazy val result = Future.collect(list.map(_.run)).map(_.toList)
}

class ClumpGetFetch[T, U](input: T, fetcher: ClumpFetcher[T, U]) extends Clump[Option[U]] {
  lazy val result = fetcher.get(input)
}

class ClumpGetOrElseFetch[T, U](input: T, default: U, fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  lazy val result = fetcher.getOrElse(input, default)
}

class ClumpApplyFetch[T, U](input: T, fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  lazy val result = fetcher(input)
}

class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
  lazy val result = clump.run.flatMap(f(_).run)
}

class ClumpRescue[T](clump: Clump[T], rescue: Throwable => Clump[T]) extends Clump[T] {
  lazy val result =
    clump.run.rescue {
      case exception => rescue(exception).run
    }
}
