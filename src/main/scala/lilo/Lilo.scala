package lilo

import com.twitter.util.Future
import com.twitter.util.Throw
import com.twitter.util.Try

trait Lilo[T] {

  def map[U](f: T => U) = flatMap(f.andThen(Lilo.value(_)))

  def flatMap[U](f: T => Lilo[U]): Lilo[U] = new LiloFlatMap(this, f)

  def join[U](other: Lilo[U]): Lilo[(T, U)] = new LiloJoin(this, other)

  def handle(f: Throwable => T): Lilo[T] = rescue(f.andThen(Lilo.value(_)))

  def rescue(f: Throwable => Lilo[T]): Lilo[T] = new LiloRescue(this, f)

  def withFilter(f: T => Boolean) = new LiloFilter(this, f)

  def run = Future.Unit.flatMap(_ => result)

  protected def result: Future[Option[T]]
}

object Lilo {

  def value[T](value: T): Lilo[T] =
    this.value(Option(value))

  def value[T](value: Option[T]): Lilo[T] =
    new LiloConst(Try(value))

  def exception[T](exception: Throwable): Lilo[T] =
    new LiloConst(Throw(exception))

  def traverse[T, U](inputs: List[T])(f: T => Lilo[U]) =
    collect(inputs.map(f))

  def collect[T](lilos: Lilo[T]*): Lilo[List[T]] =
    collect(lilos.toList)

  def collect[T](lilos: List[Lilo[T]]): Lilo[List[T]] =
    new LiloCollect(lilos)

  def source[T, U](fetch: List[T] => Future[Map[T, U]], maxBatchSize: Int = Int.MaxValue) =
    new LiloSource(fetch, maxBatchSize)
}

class LiloConst[T](value: Try[Option[T]]) extends Lilo[T] {
  lazy val result = Future.const(value)
}

class LiloJoin[A, B](a: Lilo[A], b: Lilo[B]) extends Lilo[(A, B)] {
  lazy val result =
    a.run.join(b.run).map {
      case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
      case other                        => None
    }
}

class LiloCollect[T](list: List[Lilo[T]]) extends Lilo[List[T]] {
  lazy val result =
    Future
      .collect(list.map(_.run))
      .map(_.flatten.toList)
      .map(Some(_))
}

class LiloFetch[T, U](input: T, source: LiloSource[T, U]) extends Lilo[U] {
  lazy val result = source.run(input)
}

class LiloFlatMap[T, U](lilo: Lilo[T], f: T => Lilo[U]) extends Lilo[U] {
  lazy val result =
    lilo.run.flatMap {
      case Some(value) => f(value).run
      case None        => Future.None
    }
}

class LiloRescue[T](lilo: Lilo[T], rescue: Throwable => Lilo[T]) extends Lilo[T] {
  lazy val result =
    lilo.run.rescue {
      case exception => rescue(exception).run
    }
}

class LiloFilter[T](lilo: Lilo[T], f: T => Boolean) extends Lilo[T] {
  lazy val result =
    lilo.run.map(_.filter(f))
}
