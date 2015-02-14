package io.getclump

import scala.collection.generic.CanBuildFrom

import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import scala.reflect.ClassTag

sealed trait Clump[+T] {

  private[this] val context = ClumpContext()

  def map[U](f: T => U): Clump[U] = new ClumpMap(this, f)

  def flatMap[U](f: T => Clump[U]): Clump[U] = new ClumpFlatMap(this, f)

  def join[U](other: Clump[U]): Clump[(T, U)] = new ClumpJoin(this, other)

  def handle[B >: T](f: PartialFunction[Throwable, Option[B]]): Clump[B] = new ClumpHandle(this, f)

  def rescue[B >: T](f: PartialFunction[Throwable, Clump[B]]): Clump[B] = new ClumpRescue(this, f)

  def withFilter[B >: T](f: B => Boolean): Clump[B] = filter(f)

  def filter[B >: T](f: B => Boolean): Clump[B] = new ClumpFilter(this, f)

  def orElse[B >: T](default: => Clump[B]): Clump[B] = new ClumpOrElse(this, default)

  def optional: Clump[Option[T]] = new ClumpOptional(this)

  def apply(): Future[T] = get.map(_.get)

  def getOrElse[B >: T](default: => B): Future[B] = get.map(_.getOrElse(default))

  def list[B >: T](implicit cbf: CanBuildFrom[Nothing, Nothing, B]): Future[B] =
    get.map(_.getOrElse(cbf().result))

  def get: Future[Option[T]] =
    context
      .flush(List(this))
      .flatMap { _ =>
        result
      }

  protected[getclump] def upstream: List[Clump[_]]
  protected[getclump] def downstream: Future[List[Clump[_]]]
  protected[getclump] def result: Future[Option[T]]
}

object Clump {

  def empty[T]: Clump[T] = value(scala.None)

  def value[T](value: T): Clump[T] = future(Future.value(Option(value)))

  def value[T](value: Option[T]): Clump[T] = future(Future.value(value))

  def exception[T](exception: Throwable): Clump[T] = future(Future.exception(exception))

  def future[T: ClassTag](future: Future[T]): Clump[T] = new ClumpFuture(future.map(Option(_)))

  def future[T](future: Future[Option[T]]): Clump[T] = new ClumpFuture(future)

  def traverse[T, U](inputs: T*)(f: T => Clump[U]): Clump[List[U]] = traverse(inputs.toList)(f)

  def collect[T](clumps: Clump[T]*): Clump[List[T]] = collect(clumps.toList)

  def traverse[T, U, C[_] <: Iterable[_]](inputs: C[T])(f: T => Clump[U])(implicit cbf: CanBuildFrom[Nothing, U, C[U]]): Clump[C[U]] =
    collect(inputs.toList.asInstanceOf[List[T]].map(f)).map(cbf.apply().++=(_).result())

  def collect[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]): Clump[C[T]] =
    new ClumpCollect(clumps)

  def join[A, B](a: Clump[A], b: Clump[B]): Clump[(A, B)] =
    a.join(b)

  def join[A, B, C](a: Clump[A], b: Clump[B], c: Clump[C]): Clump[(A, B, C)] =
    (join(a, b).join(c)).map {
      case ((a, b), c) => (a, b, c)
    }

  def join[A, B, C, D](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D]): Clump[(A, B, C, D)] =
    (join(a, b, c).join(d)).map {
      case ((a, b, c), d) => (a, b, c, d)
    }

  def join[A, B, C, D, E](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E]): Clump[(A, B, C, D, E)] =
    (join(a, b, c, d).join(e)).map {
      case ((a, b, c, d), e) => (a, b, c, d, e)
    }

  def join[A, B, C, D, E, F](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F]): Clump[(A, B, C, D, E, F)] =
    (join(a, b, c, d, e).join(f)).map {
      case ((a, b, c, d, e), f) => (a, b, c, d, e, f)
    }

  def join[A, B, C, D, E, F, G](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G]): Clump[(A, B, C, D, E, F, G)] =
    (join(a, b, c, d, e, f).join(g)).map {
      case ((a, b, c, d, e, f), g) => (a, b, c, d, e, f, g)
    }

  def join[A, B, C, D, E, F, G, H](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H]): Clump[(A, B, C, D, E, F, G, H)] =
    (join(a, b, c, d, e, f, g).join(h)).map {
      case ((a, b, c, d, e, f, g), h) => (a, b, c, d, e, f, g, h)
    }

  def join[A, B, C, D, E, F, G, H, I](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H], i: Clump[I]): Clump[(A, B, C, D, E, F, G, H, I)] =
    (join(a, b, c, d, e, f, g, h).join(i)).map {
      case ((a, b, c, d, e, f, g, h), i) => (a, b, c, d, e, f, g, h, i)
    }

  def join[A, B, C, D, E, F, G, H, I, J](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H], i: Clump[I], j: Clump[J]): Clump[(A, B, C, D, E, F, G, H, I, J)] =
    (join(a, b, c, d, e, f, g, h, i).join(j)).map {
      case ((a, b, c, d, e, f, g, h, i), j) => (a, b, c, d, e, f, g, h, i, j)
    }

  def source[C] = new {
    def apply[T, U](fetch: C => Future[Iterable[U]])(keyExtractor: U => T)(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSource[T, U] =
      ClumpSource(fetch)(keyExtractor)
  }

  def sourceFrom[C] = new {
    def apply[T, U](fetch: C => Future[Iterable[(T, U)]])(implicit cbf: CanBuildFrom[Nothing, T, C]): ClumpSource[T, U] =
      ClumpSource.from(fetch)
  }

  def sourceZip[T, U](fetch: List[T] => Future[List[U]]): ClumpSource[T, U] =
    ClumpSource.zip(fetch)
}

private[getclump] class ClumpFuture[T](val result: Future[Option[T]]) extends Clump[T] {
  val upstream = List()
  val downstream = result.liftToTry.map(_ => List())
}

private[getclump] class ClumpFetch[T, U](input: T, val fetcher: ClumpFetcher[T, U]) extends Clump[U] {
  val upstream = List()
  val downstream = Future.value(List())
  val result = fetcher.get(input)
}

private[getclump] class ClumpJoin[A, B](a: Clump[A], b: Clump[B]) extends Clump[(A, B)] {
  val upstream = List(a, b)
  val downstream = Future.value(List())
  val result =
    a.result.join(b.result)
      .map {
        case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
        case _                            => None
      }
}

private[getclump] class ClumpCollect[T, C[_] <: Iterable[_]](clumps: C[Clump[T]])(implicit cbf: CanBuildFrom[C[Clump[T]], T, C[T]]) extends Clump[C[T]] {
  val upstream =
    clumps.toList.asInstanceOf[List[Clump[T]]]
  val downstream =
    Future.value(List())
  val result =
    Future
      .collect(upstream.map(_.result))
      .map(_.flatten)
      .map(cbf.apply().++=(_).result)
      .map(Some(_))
}

private[getclump] class ClumpMap[T, U](clump: Clump[T], f: T => U) extends Clump[U] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.map(_.map(f))
}

private[getclump] class ClumpFlatMap[T, U](clump: Clump[T], f: T => Clump[U]) extends Clump[U] {
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

private[getclump] class ClumpHandle[T](clump: Clump[T], f: PartialFunction[Throwable, Option[T]]) extends Clump[T] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.handle(f)
}

private[getclump] class ClumpRescue[T](clump: Clump[T], rescue: PartialFunction[Throwable, Clump[T]]) extends Clump[T] {
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

private[getclump] class ClumpFilter[T](clump: Clump[T], f: T => Boolean) extends Clump[T] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result =
    clump.result.map(_.filter(f))
}

private[getclump] class ClumpOrElse[T](clump: Clump[T], default: => Clump[T]) extends Clump[T] {
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

private[getclump] class ClumpOptional[T](clump: Clump[T]) extends Clump[Option[T]] {
  val upstream = List(clump)
  val downstream = Future.value(List())
  val result = clump.result.map(Some(_))
}
