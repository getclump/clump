package io.getclump

protected[getclump] trait Joins {
  /**
   * Join 2 clumps into a single clump containing a Tuple2
   */
  def join[A, B](a: Clump[A], b: Clump[B]): Clump[(A, B)] =
    a.join(b)

  /**
   * Join 3 clumps into a single clump containing a Tuple3
   */
  def join[A, B, C](a: Clump[A], b: Clump[B], c: Clump[C]): Clump[(A, B, C)] =
    (join(a, b).join(c)).map {
      case ((a, b), c) => (a, b, c)
    }

  /**
   * Join 4 clumps into a single clump containing a Tuple4
   */
  def join[A, B, C, D](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D]): Clump[(A, B, C, D)] =
    (join(a, b, c).join(d)).map {
      case ((a, b, c), d) => (a, b, c, d)
    }

  /**
   * Join 5 clumps into a single clump containing a Tuple5
   */
  def join[A, B, C, D, E](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E]): Clump[(A, B, C, D, E)] =
    (join(a, b, c, d).join(e)).map {
      case ((a, b, c, d), e) => (a, b, c, d, e)
    }

  /**
   * Join 6 clumps into a single clump containing a Tuple6
   */
  def join[A, B, C, D, E, F](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F]): Clump[(A, B, C, D, E, F)] =
    (join(a, b, c, d, e).join(f)).map {
      case ((a, b, c, d, e), f) => (a, b, c, d, e, f)
    }

  /**
   * Join 7 clumps into a single clump containing a Tuple7
   */
  def join[A, B, C, D, E, F, G](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G]): Clump[(A, B, C, D, E, F, G)] =
    (join(a, b, c, d, e, f).join(g)).map {
      case ((a, b, c, d, e, f), g) => (a, b, c, d, e, f, g)
    }

  /**
   * Join 8 clumps into a single clump containing a Tuple8
   */
  def join[A, B, C, D, E, F, G, H](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H]): Clump[(A, B, C, D, E, F, G, H)] =
    (join(a, b, c, d, e, f, g).join(h)).map {
      case ((a, b, c, d, e, f, g), h) => (a, b, c, d, e, f, g, h)
    }

  /**
   * Join 9 clumps into a single clump containing a Tuple9
   */
  def join[A, B, C, D, E, F, G, H, I](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H], i: Clump[I]): Clump[(A, B, C, D, E, F, G, H, I)] =
    (join(a, b, c, d, e, f, g, h).join(i)).map {
      case ((a, b, c, d, e, f, g, h), i) => (a, b, c, d, e, f, g, h, i)
    }

  /**
   * Join 10 clumps into a single clump containing a Tuple10
   */
  def join[A, B, C, D, E, F, G, H, I, J](a: Clump[A], b: Clump[B], c: Clump[C], d: Clump[D], e: Clump[E], f: Clump[F], g: Clump[G], h: Clump[H], i: Clump[I], j: Clump[J]): Clump[(A, B, C, D, E, F, G, H, I, J)] =
    (join(a, b, c, d, e, f, g, h, i).join(j)).map {
      case ((a, b, c, d, e, f, g, h, i), j) => (a, b, c, d, e, f, g, h, i, j)
    }
}
