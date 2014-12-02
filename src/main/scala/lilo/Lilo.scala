package lilo

import com.twitter.util.Future

trait Lilo[T] {

  def map[U](f: T => U) = flatMap(f.andThen(Lilo.value))

  def flatMap[U](f: T => Lilo[U]): Lilo[U] = new LiloFlatMap(this, f)

  def join[U](other: Lilo[U]): Lilo[(T, U)] = new LiloJoin(this, other)

  def handle(f: Throwable => T): Lilo[T] = rescue(f.andThen(Lilo.value))

  def rescue(f: Throwable => Lilo[T]): Lilo[T] = new LiloRescue(this, f)

  def withFilter(p: T => Boolean) = run.filter(_.forall(p))

  def run: Future[Option[T]]
}

object Lilo {

  def value[T](value: T): Lilo[T] = new LiloConst(value)

  def traverse[T, U](inputs: List[T])(f: T => Lilo[U]) = collect(inputs.map(f))

  def collect[T](lilos: List[Lilo[T]]): Lilo[List[T]] = new LiloCollect(lilos)

  def source[T, U](fetch: List[T] => Future[Map[T, U]]) = new LiloSource(fetch)
}

class LiloConst[T](value: T) extends Lilo[T] {
  def run = Future.value(Some(value))
}

class LiloJoin[A, B](a: Lilo[A], b: Lilo[B]) extends Lilo[(A, B)] {
  def run =
    a.run.join(b.run).map {
      case (Some(valueA), Some(valueB)) => Some(valueA, valueB)
      case other                        => None
    }
}

class LiloCollect[T](list: List[Lilo[T]]) extends Lilo[List[T]] {
  def run =
    Future
      .collect(list.map(_.run))
      .map(_.flatten.toList)
      .map(Some(_))
}

class LiloFetch[T, U](input: T, source: LiloSource[T, U]) extends Lilo[U] {
  def run = source.run(input)
}

class LiloFlatMap[T, U](lilo: Lilo[T], f: T => Lilo[U]) extends Lilo[U] {
  def run =
    lilo.run.flatMap {
      case Some(value) => f(value).run
      case None        => Future.None
    }
}

class LiloRescue[T](lilo: Lilo[T], rescue: Throwable => Lilo[T]) extends Lilo[T] {
  def run =
    lilo.run.rescue {
      case exception => rescue(exception).run
    }
}

class LiloSource[T, U](fetch: List[T] => Future[Map[T, U]]) {

  private var pending = Set[T]()
  private var fetched = Map[T, Future[Option[U]]]()

  def get(input: T): Lilo[U] = {
    synchronized(pending += input)
    new LiloFetch(input, this)
  }

  def get(inputs: List[T]): Lilo[List[U]] =
    Lilo.collect(inputs.map(get(_)))

  private[lilo] def run(input: T) =
    fetched.get(input).getOrElse {
      flush.flatMap { _ =>
        fetched(input)
      }
    }

  private def flush =
    synchronized {
      val results = fetch(pending.toList)
      for (input <- pending)
        fetched += input -> results.map(_.get(input))
      pending = Set()
      results
    }
}