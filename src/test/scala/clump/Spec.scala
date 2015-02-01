package clump

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import com.twitter.util.Await
import com.twitter.util.TimeConversions.intToTimeableNumber

trait Spec extends Specification with Mockito with NoTimeConversions {

  protected def clumpResult[T](clump: Clump[T]) =
    Await.result(clump.get, 10 seconds)
}
