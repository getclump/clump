package lilo

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

class LiloExecutionSpec extends Spec {

  "batches requests" >> {

    "for multiple lilos created from traversed inputs" in {
      ok
    }

    "for multiple lilos collected into only one lilo" in {
      ok
    }

    "for lilos created inside nested flatmaps" in {
      ok
    }

    "for lilos composed using for comprehension" >> {

      "one level" in {
        ok
      }

      "two levels" in {
        ok
      }

      "many levels" in {
        ok
      }

      "with a filter condition" in {
        ok
      }

      "composition using a join" in {
        ok
      }
    }
  }

  "executes joined lilos in parallel" in {
    ok
  }

  "short-circuits the computation in case of a failure" in {
    ok
  }
}