package io.getclump

import utest._
import scala.reflect.ClassTag

trait Spec extends TestSuite {

  object NoError extends Exception

  protected def assertFailure[T: ClassTag](f: Future[_]) =
    f.map(_ => throw NoError).recover {
      case NoError => throw new IllegalStateException("A failure was expected.")
      case e: T    => None
    }

  protected def assertFailure[T: ClassTag](f: Clump[_]): Future[_] =
    assertFailure[T](f.get)

  protected def assertResult[T](clump: Clump[T], expected: T) =
    clump.get.map { result =>
      assert(result == expected)
    }
}
