package io.getclump

private[getclump] case class RunOnce[T, U](f: T => Clump[U]) extends (T => Clump[U]) {
  var cache: Option[Clump[U]] = None
  override def apply(t: T) = synchronized {
    cache.getOrElse {
      val ans = f(t)
      cache = Some(ans)
      ans
    }
  }
}

