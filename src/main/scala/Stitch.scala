import com.twitter.util.Future

trait Stitch[T] {
  def map[U](f: T => U): Stitch[U]

  def flatMap[U](f: T => Stitch[U]): Stitch[U]

  def handle(f: Throwable => T): Stitch[T]

  def rescue(f: Throwable => Stitch[T]): Stitch[T]

  def run(): Future[T]
}

object Stitch {
  def value[T](t: T): Stitch[T] = ???

  def join[A, B](a: Stitch[A], b: Stitch[B]): Stitch[(A, B)] = ???

  def collect[T](ss: Seq[Stitch[T]]): Stitch[Seq[T]] = ???

  def traverse[T, U](ts: Seq[T])(f: T => Stitch[U]): Stitch[Seq[U]] = ???

  def run[T](s: Stitch[T]): Future[T] = ???

  def call[C,T](call: C,group: SeqGroup[C,T]): Stitch[T] = ???
}

trait SeqGroup[C,T] {
  def run(calls: Seq[C]): Future[Seq[T]]
}


