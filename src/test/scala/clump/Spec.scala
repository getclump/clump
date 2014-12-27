package clump

import com.twitter.util.TimeConversions._
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.twitter.util.Await
import org.specs2.time.NoTimeConversions

trait Spec extends Specification with Mockito with NoTimeConversions {
  
  def clumpResult[T](clump: Clump[T]) =
    Await.result(clump.get, 10 seconds)
}
