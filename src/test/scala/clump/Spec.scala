package clump

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.twitter.util.Await

trait Spec extends Specification with Mockito {
  
  def resultOf[T](clump: Clump[T]) =
    Await.result(clump.run)
}
