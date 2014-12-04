package lilo

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.twitter.util.Await

trait Spec extends Specification with Mockito {
  
  def resultOf[T](lilo: Lilo[T]) =
    Await.result(lilo.run)
}
