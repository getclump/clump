package lilo

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope

class LiloSpec extends Specification with Mockito {

  "the Lilo object" >> {

    "allows to create a constant lilo" >> {
      "from a value (Lilo.value)" in {
        ok
      }
      "from an option (Lilo.value)" in {
        ok
      }
      "failed (Lilo.exception)" in {
        ok
      }
    }

    "allows to create a lilo traversing multiple inputs (Lilo.traverse)" in {
      ok
    }

    "allows to collect multiple lilos in only one (Lilo.collect)" in {
      ok
    }

    "allows to create a lilo source (Lilo.source)" >> {
      "using the specified function" in {
        ok
      }
      "using the specified max batch size" in {
        ok
      }
    }
  }

  "a Lilo instance" >> {
    "can be mapped to a new lilo" >> {
      "using simple a value transformation (lilo.map)" in {
        ok
      }
      "using a transformation that creates a new lilo (lilo.flatMap)" in {
        ok
      }
    }
    "can be joined with another lilo and produce a new lilo with the value of both (lilo.join)" in {
      ok
    }
    "allows to recover from failures" >> {
      "using a function that recovers using a new value (lilo.handle)" in {
        ok
      }
      "using a function that recovers the failure using a new lilo (lilo.rescue)" in {
        ok
      }
    }
    "can have its result filtered (lilo.withFilter)" in {
      ok
    }
    "can be materialized and return a future (lilo.run)" in {
      ok
    }
  }

  "The lilo execution model batches requests" >> {
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
      "composition using a join" in {
        ok
      }
    }
  }
}
