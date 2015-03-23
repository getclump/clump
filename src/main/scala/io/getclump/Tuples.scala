package io.getclump

import com.twitter.util.Future

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
}
