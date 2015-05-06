package io.getclump

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

trait Spec extends Specification with Mockito with NoTimeConversions {

  protected def clumpResult[T](clump: Clump[T]) =
    blockOn(clump.get)
}
