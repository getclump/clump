package io.getclump

import utest._

trait Spec extends TestSuite {

  protected def clumpResult[T](clump: Clump[T]) =
    awaitResult(clump.get)
}
