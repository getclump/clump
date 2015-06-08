package io.getclump


protected[getclump] trait Tuples {
  protected[getclump] def normalize1[A, B] = (inputs: (A, B)) => inputs match {
    case (a, b) => ((a), b)
  }

  protected[getclump] def normalize2[A, B, C] = (inputs: (A, B, C)) => inputs match {
    case (a, b, c) => ((a, b), c)
  }

  protected[getclump] def normalize3[A, B, C, D] = (inputs: (A, B, C, D)) => inputs match {
    case (a, b, c, d) => ((a, b, c), d)
  }

  protected[getclump] def normalize4[A, B, C, D, E] = (inputs: (A, B, C, D, E)) => inputs match {
    case (a, b, c, d, e) => ((a, b, c, d), e)
  }

  protected[getclump] def denormalize1[A, B] = (trunk: A, last: B) => (trunk, last) match {
    case ((a), b) => (a, b)
  }

  protected[getclump] def denormalize2[A, B, C] = (trunk: (A, B), last: C) => (trunk, last) match {
    case ((a, b), c) => (a, b, c)
  }

  protected[getclump] def denormalize3[A, B, C, D] = (trunk: (A, B, C), last: D) => (trunk, last) match {
    case ((a, b, c), d) => (a, b, c, d)
  }

  protected[getclump] def denormalize4[A, B, C, D, E] = (trunk: (A, B, C, D), last: E) => (trunk, last) match {
    case ((a, b, c, d), e) => (a, b, c, d, e)
  }

  protected[getclump] def fetch1[A, B, C](fetch: (A, B) => Future[Iterable[C]]) = (params: (A), values: B) => (params, values) match {
    case ((a), b) => fetch(a, b)
  }

  protected[getclump] def fetch2[A, B, C, D](fetch: (A, B, C) => Future[Iterable[D]]) = (params: (A, B), values: C) => (params, values) match {
    case ((a, b), c) => fetch(a, b, c)
  }

  protected[getclump] def fetch3[A, B, C, D, E](fetch: (A, B, C, D) => Future[Iterable[E]]) = (params: (A, B, C), values: D) => (params, values) match {
    case ((a, b, c), d) => fetch(a, b, c, d)
  }

  protected[getclump] def fetch4[A, B, C, D, E, F](fetch: (A, B, C, D, E) => Future[Iterable[F]]) = (params: (A, B, C, D), values: E) => (params, values) match {
    case ((a, b, c, d), e) => fetch(a, b, c, d, e)
  }

  protected[getclump] def adapt[A, B](fetch: A => Future[B]) = (keys: Iterable[A]) =>
    Future.sequence(keys.toSeq.map(key => fetch(key).map(value => key -> value))).map(_.toMap)

  protected[getclump] def adapt1[A, B, C](fetch: (A, B) => Future[C]) = (a: A, keys: Iterable[B]) =>
    Future.sequence(keys.toSeq.map(key => fetch(a, key).map(value => key -> value))).map(_.toMap)

  protected[getclump] def adapt2[A, B, C, D](fetch: (A, B, C) => Future[D]) = (a: A, b: B, keys: Iterable[C]) =>
    Future.sequence(keys.toSeq.map(key => fetch(a, b, key).map(value => key -> value))).map(_.toMap)

  protected[getclump] def adapt3[A, B, C, D, E](fetch: (A, B, C, D) => Future[E]) = (a: A, b: B, c: C, keys: Iterable[D]) =>
    Future.sequence(keys.toSeq.map(key => fetch(a, b, c, key).map(value => key -> value))).map(_.toMap)

  protected[getclump] def adapt4[A, B, C, D, E, F](fetch: (A, B, C, D, E) => Future[F]) = (a: A, b: B, c: C, d: D, keys: Iterable[E]) =>
    Future.sequence(keys.toSeq.map(key => fetch(a, b, c, d, key).map(value => key -> value))).map(_.toMap)
}
