package io.getclump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ProblemSpec extends Spec {

  val source = Clump.sourceSingle { (x: Int) => Future.successful(x) }

  "doesn't work with flatMap" >> {
    val clump = for {
      one <- Clump.value(1)
      two <- source.get(2) // ClumpFetch needs to be inside ClumpFlatMap
    } yield (one, two)

    clump.get must beEqualTo(Some((1, 2))).await // timeout!
  }

  "doesn't work with rescue" >> {
    val good = Clump.failed(new Exception()).rescue {
      case _ => Clump.value(1)
    }

    good.get must beEqualTo(Some(1)).await // passes

    val bad = Clump.failed(new Exception()).rescue {
      case _ => source.get(2) // again, ClumpFetch needs to be inside ClumpRescue
    }

    bad.get must beEqualTo(Some(2)).await // timeout!
  }

  "doesn't work with orElse" >> {
    val clump = Clump.empty.orElse(source.get(1)) // ClumpFetch inside ClumpOrElse

    clump.get must beEqualTo(Some(1)).await // timeout!
  }
}
