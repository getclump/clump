package io.getclump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SourcesSpec extends Spec {

  "creates a clump source" >> {
    "set input" in {
      def fetch(inputs: Set[Int]) = Future.successful(inputs.map(i => i -> i.toString).toMap)
      val source = Clump.source(fetch _)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "list input" in {
      def fetch(inputs: List[Int]) = Future.successful(inputs.map(i => i -> i.toString).toMap)
      val source = Clump.source(fetch _)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "extra params" >> {
      "one" in {
        def fetch(param1: Int, values: List[Int]) =
          Future(values.map(v => v -> v * param1).toMap)
        val source = Clump.source(fetch _)
        val clump = Clump.collect(source.get(2, 3), source.get(2, 4), source.get(3, 5))
        clumpResult(clump) mustEqual Some(List(6, 8, 15))
      }
      "two" in {
        def fetch(param1: Int, param2: String, values: List[Int]) =
          Future(values.map(v => v -> List(param1, param2, v)).toMap)
        val source = Clump.source(fetch _)
        val clump = Clump.collect(source.get(1, "2", 3), source.get(1, "2", 4), source.get(2, "3", 5))
        clumpResult(clump) mustEqual Some(List(List(1, "2", 3), List(1, "2", 4), List(2, "3", 5)))
      }
      "three" in {
        def fetch(param1: Int, param2: String, param3: List[String], values: List[Int]) =
          Future(values.map(v => v -> List(param1, param2, param3, v)).toMap)
        val source = Clump.source(fetch _)
        val clump = Clump.collect(source.get(1, "2", List("a"), 3), source.get(1, "2", List("a"), 4), source.get(2, "3", List("b"), 5))
        clumpResult(clump) mustEqual Some(List(List(1, "2", List("a"), 3), List(1, "2", List("a"), 4), List(2, "3", List("b"), 5)))
      }
      "four" in {
        def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, values: List[Int]) =
          Future(values.map(v => v -> List(param1, param2, param3, param4, v)).toMap)
        val source = Clump.source(fetch _)
        val clump = Clump.collect(source.get(1, "2", List("a"), true, 3), source.get(1, "2", List("a"), true, 4), source.get(2, "3", List("b"), false, 5))
        clumpResult(clump) mustEqual Some(List(List(1, "2", List("a"), true, 3), List(1, "2", List("a"), true, 4), List(2, "3", List("b"), false, 5)))
      }
    }
  }

  "creates a clump source with key function" >> {
    "set input" in {
      def fetch(inputs: Set[Int]) = Future.successful(inputs.map(_.toString))
      val source = Clump.source(fetch _)(_.toInt)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "seq input" in {
      def fetch(inputs: Seq[Int]) = Future.successful(inputs.map(_.toString))
      val source = Clump.source(fetch _)(_.toInt)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "extra params" >> {
      "one" in {
        def fetch(param1: Int, values: List[Int]) = Future(values.map((param1, _)))
        val source = Clump.source(fetch _)(_._2)
        val clump = Clump.collect(source.get(2, 3), source.get(2, 4), source.get(3, 5))
        clumpResult(clump) mustEqual Some(List((2, 3), (2, 4), (3, 5)))
      }
      "two" in {
        def fetch(param1: Int, param2: String, values: List[Int]) = Future(values.map((param1, param2, _)))
        val source = Clump.source(fetch _)(_._3)
        val clump = Clump.collect(source.get(1, "2", 3), source.get(1, "2", 4), source.get(2, "3", 5))
        clumpResult(clump) mustEqual Some(List((1, "2", 3), (1, "2", 4), (2, "3", 5)))
      }
      "three" in {
        def fetch(param1: Int, param2: String, param3: List[String], values: List[Int]) =
          Future(values.map((param1, param2, param3, _)))
        val source = Clump.source(fetch _)(_._4)
        val clump = Clump.collect(source.get(1, "2", List("a"), 3), source.get(1, "2", List("a"), 4), source.get(2, "3", List("b"), 5))
        clumpResult(clump) mustEqual Some(List((1, "2", List("a"), 3), (1, "2", List("a"), 4), (2, "3", List("b"), 5)))
      }
      "four" in {
        def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, values: List[Int]) =
          Future(values.map((param1, param2, param3, param4, _)))
        val source = Clump.source(fetch _)(_._5)
        val clump = Clump.collect(source.get(1, "2", List("a"), true, 3), source.get(1, "2", List("a"), true, 4), source.get(2, "3", List("b"), false, 5))
        clumpResult(clump) mustEqual Some(List((1, "2", List("a"), true, 3), (1, "2", List("a"), true, 4), (2, "3", List("b"), false, 5)))
      }
    }
  }

  "creates a clump source that allows marking individual entries as failed" >> {
    "list input" in {
      def fetch(inputs: List[Int]) = Future.successful(inputs.map(x => x -> Try(1 / x)).toMap)
      val source = Clump.sourceTry(fetch _)
      clumpResult(source.get(1)) mustEqual Some(1)
      clumpResult(source.get(0)) must throwA[ArithmeticException]
    }
    "extra params" >> {
      "one" in {
        def fetch(param1: Int, inputs: List[Int]) = Future.successful(inputs.map(x => x -> Try(1 / (x + param1))).toMap)
        val source = Clump.sourceTry(fetch _)
        clumpResult(source.get(1, 0)) mustEqual Some(1)
        clumpResult(source.get(1, -1)) must throwA[ArithmeticException]
      }
      "two" in {
        def fetch(param1: Int, param2: Int, inputs: List[Int]) = Future.successful(inputs.map(x => x -> Try(1 / (x + param1 + param2))).toMap)
        val source = Clump.sourceTry(fetch _)
        clumpResult(source.get(1, 0, 0)) mustEqual Some(1)
        clumpResult(source.get(1, 1, -2)) must throwA[ArithmeticException]
      }
      "three" in {
        def fetch(param1: Int, param2: Int, param3: Int, inputs: List[Int]) = Future.successful(inputs.map(x => x -> Try(1 / (x + param1 + param2 + param3))).toMap)
        val source = Clump.sourceTry(fetch _)
        clumpResult(source.get(1, 0, 0, 0)) mustEqual Some(1)
        clumpResult(source.get(1, 1, 1, -3)) must throwA[ArithmeticException]
      }
      "four" in {
        def fetch(param1: Int, param2: Int, param3: Int, param4: Int, inputs: List[Int]) = Future.successful(inputs.map(x => x -> Try(1 / (x + param1 + param2 + param3 + param4))).toMap)
        val source = Clump.sourceTry(fetch _)
        clumpResult(source.get(1, 0, 0, 0, 0)) mustEqual Some(1)
        clumpResult(source.get(1, 1, 1, 1, -4)) must throwA[ArithmeticException]
      }
    }
  }

  "creates a clump source with zip as the key function" >> {
    "list input" in {
      def fetch(inputs: List[Int]) = Future.successful(inputs.map(_.toString))
      val source = Clump.sourceZip(fetch _)
      clumpResult(source.get(1)) mustEqual Some("1")
    }
    "extra params" >> {
      "one" in {
        def fetch(param1: Int, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_.toString))
        val source = Clump.sourceZip(fetch _)
        clumpResult(source.get(1, 2)) mustEqual Some("3")
      }
      "two" in {
        def fetch(param1: Int, param2: String, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2))
        val source = Clump.sourceZip(fetch _)
        clumpResult(source.get(1, "a", 2)) mustEqual Some("3a")
      }
      "three" in {
        def fetch(param1: Int, param2: String, param3: List[String], inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2).map(_ + param3.fold("")(_ + _)))
        val source = Clump.sourceZip(fetch _)
        clumpResult(source.get(1, "a", List("b", "c"), 2)) mustEqual Some("3abc")
      }
      "four" in {
        def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2).map(_ + param3.fold("")(_ + _)).map(_ + s"-$param4"))
        val source = Clump.sourceZip(fetch _)
        clumpResult(source.get(1, "a", List("b", "c"), true, 2)) mustEqual Some("3abc-true")
      }
    }
  }

  "creates a clump source from a singly keyed fetch function" >> {
    "single key input" in {
      def fetch(input: Int) = Future.successful(Set(input, input + 1, input + 2))

      val source = Clump.sourceSingle(fetch _)

      clumpResult(source.get(1)) mustEqual Some(Set(1, 2, 3))
      clumpResult(source.get(2)) mustEqual Some(Set(2, 3, 4))
      clumpResult(source.get(List(1, 2))) mustEqual Some(List(Set(1, 2, 3), Set(2, 3, 4)))
    }
    "function with failure" in {
      def fetch(input: Int) = if (input % 2 == 0) Future.successful(input / 2) else Future.failed(new ArithmeticException)

      val source = Clump.sourceSingle(fetch _)

      clumpResult(source.get(2)) mustEqual Some(1)
      clumpResult(source.get(1)) must throwA[ArithmeticException]
    }
    "extra params" >> {
      "one" in {
        def fetch(param1: String, input: Int) = Future.successful(input + param1)
        val source = Clump.sourceSingle(fetch _)
        clumpResult(source.get("a", 2)) mustEqual Some("2a")
      }
      "two" in {
        def fetch(param1: String, param2: List[Int], input: Int) = Future.successful(input + param1 + param2.mkString)
        val source = Clump.sourceSingle(fetch _)
        clumpResult(source.get("a", List(1, 2), 3)) mustEqual Some("3a12")
      }
      "three" in {
        def fetch(param1: String, param2: List[Int], param3: Int, input: Int) = Future.successful(input + param1 + param2.mkString + param3)
        val source = Clump.sourceSingle(fetch _)
        clumpResult(source.get("a", List(1, 2), 3, 4)) mustEqual Some("4a123")
      }
      "four" in {
        def fetch(param1: String, param2: List[Int], param3: Int, param4: List[String], input: Int) = Future.successful(input + param1 + param2.mkString + param3 + param4.mkString)
        val source = Clump.sourceSingle(fetch _)
        clumpResult(source.get("a", List(1, 2), 3, List("b","c"), 4)) mustEqual Some("4a123bc")
      }
    }
  }

  "creates a clump source from various input/ouput type fetch functions" in {
    def setToSet: Set[Int] => Future[Set[String]] = { inputs => Future.successful(inputs.map(_.toString)) }
    def listToList: List[Int] => Future[List[String]] = { inputs => Future.successful(inputs.map(_.toString)) }
    def iterableToIterable: Iterable[Int] => Future[Iterable[String]] = { inputs => Future.successful(inputs.map(_.toString)) }
    def setToList: Set[Int] => Future[List[String]] = { inputs => Future.successful(inputs.map(_.toString).toList) }
    def listToSet: List[Int] => Future[Set[String]] = { inputs => Future.successful(inputs.map(_.toString).toSet) }
    def setToIterable: Set[Int] => Future[Iterable[String]] = { inputs => Future.successful(inputs.map(_.toString)) }
    def listToIterable: List[Int] => Future[Iterable[String]] = { inputs => Future.successful(inputs.map(_.toString)) }
    def iterableToList: Iterable[Int] => Future[List[String]] = { inputs => Future.successful(inputs.map(_.toString).toList) }
    def iterableToSet: Iterable[Int] => Future[List[String]] = { inputs => Future.successful(inputs.map(_.toString).toList) }

    def testSource(source: ClumpSource[Int, String]) =
      clumpResult(source.get(List(1, 2))) mustEqual Some(List("1", "2"))

    def extractId(string: String) = string.toInt

    testSource(Clump.source(setToSet)(extractId))
    testSource(Clump.source(listToList)(extractId))
    testSource(Clump.source(iterableToIterable)(extractId))
    testSource(Clump.source(setToList)(extractId))
    testSource(Clump.source(listToSet)(extractId))
    testSource(Clump.source(setToIterable)(extractId))
    testSource(Clump.source(listToIterable)(extractId))
    testSource(Clump.source(iterableToList)(extractId))
    testSource(Clump.source(iterableToSet)(extractId))
  }

  "creates a clump source from various input/ouput type fetch functions" in {

    def setToMap: Set[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }
    def listToMap: List[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }
    def iterableToMap: Iterable[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }

    def testSource(source: ClumpSource[Int, String]) =
      clumpResult(source.get(List(1, 2))) mustEqual Some(List("1", "2"))

    testSource(Clump.source(setToMap))
    testSource(Clump.source(listToMap))
    testSource(Clump.source(iterableToMap))
  }
}