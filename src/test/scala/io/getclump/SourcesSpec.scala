package io.getclump

import utest._

object SourcesSpec extends Spec {

  val tests = TestSuite {

    "creates a clump source" - {
      "set input" - {
        def fetch(inputs: Set[Int]) = Future.successful(inputs.map(i => i -> i.toString).toMap)
        val source = Clump.source(fetch _)
        assertResult(source.get(1), Some("1"))
      }
      "list input" - {
        def fetch(inputs: List[Int]) = Future.successful(inputs.map(i => i -> i.toString).toMap)
        val source = Clump.source(fetch _)
        assertResult(source.get(1), Some("1"))
      }
      "extra params" - {
        "one" - {
          def fetch(param1: Int, values: List[Int]) =
            Future(values.map(v => v -> v * param1).toMap)
          val source = Clump.source(fetch _)
          val clump = Clump.collect(source.get(2, 3), source.get(2, 4), source.get(3, 5))
          assertResult(clump, Some(List(6, 8, 15)))
        }
        "two" - {
          def fetch(param1: Int, param2: String, values: List[Int]) =
            Future(values.map(v => v -> List(param1, param2, v)).toMap)
          val source = Clump.source(fetch _)
          val clump = Clump.collect(source.get(1, "2", 3), source.get(1, "2", 4), source.get(2, "3", 5))
          assertResult(clump, Some(List(List(1, "2", 3), List(1, "2", 4), List(2, "3", 5))))
        }
        "three" - {
          def fetch(param1: Int, param2: String, param3: List[String], values: List[Int]) =
            Future(values.map(v => v -> List(param1, param2, param3, v)).toMap)
          val source = Clump.source(fetch _)
          val clump = Clump.collect(source.get(1, "2", List("a"), 3), source.get(1, "2", List("a"), 4), source.get(2, "3", List("b"), 5))
          assertResult(clump, Some(List(List(1, "2", List("a"), 3), List(1, "2", List("a"), 4), List(2, "3", List("b"), 5))))
        }
        "four" - {
          def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, values: List[Int]) =
            Future(values.map(v => v -> List(param1, param2, param3, param4, v)).toMap)
          val source = Clump.source(fetch _)
          val clump = Clump.collect(source.get(1, "2", List("a"), true, 3), source.get(1, "2", List("a"), true, 4), source.get(2, "3", List("b"), false, 5))
          assertResult(clump, Some(List(List(1, "2", List("a"), true, 3), List(1, "2", List("a"), true, 4), List(2, "3", List("b"), false, 5))))
        }
      }
    }

    "creates a clump source with key function" - {
      "set input" - {
        def fetch(inputs: Set[Int]) = Future.successful(inputs.map(_.toString))
        val source = Clump.source(fetch _)(_.toInt)
        assertResult(source.get(1), Some("1"))
      }
      "seq input" - {
        def fetch(inputs: Seq[Int]) = Future.successful(inputs.map(_.toString))
        val source = Clump.source(fetch _)(_.toInt)
        assertResult(source.get(1), Some("1"))
      }
      "extra params" - {
        "one" - {
          def fetch(param1: Int, values: List[Int]) = Future(values.map((param1, _)))
          val source = Clump.source(fetch _)(_._2)
          val clump = Clump.collect(source.get(2, 3), source.get(2, 4), source.get(3, 5))
          assertResult(clump, Some(List((2, 3), (2, 4), (3, 5))))
        }
        "two" - {
          def fetch(param1: Int, param2: String, values: List[Int]) = Future(values.map((param1, param2, _)))
          val source = Clump.source(fetch _)(_._3)
          val clump = Clump.collect(source.get(1, "2", 3), source.get(1, "2", 4), source.get(2, "3", 5))
          assertResult(clump, Some(List((1, "2", 3), (1, "2", 4), (2, "3", 5))))
        }
        "three" - {
          def fetch(param1: Int, param2: String, param3: List[String], values: List[Int]) =
            Future(values.map((param1, param2, param3, _)))
          val source = Clump.source(fetch _)(_._4)
          val clump = Clump.collect(source.get(1, "2", List("a"), 3), source.get(1, "2", List("a"), 4), source.get(2, "3", List("b"), 5))
          assertResult(clump, Some(List((1, "2", List("a"), 3), (1, "2", List("a"), 4), (2, "3", List("b"), 5))))
        }
        "four" - {
          def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, values: List[Int]) =
            Future(values.map((param1, param2, param3, param4, _)))
          val source = Clump.source(fetch _)(_._5)
          val clump = Clump.collect(source.get(1, "2", List("a"), true, 3), source.get(1, "2", List("a"), true, 4), source.get(2, "3", List("b"), false, 5))
          assertResult(clump, Some(List((1, "2", List("a"), true, 3), (1, "2", List("a"), true, 4), (2, "3", List("b"), false, 5))))
        }
      }
    }

    "creates a clump source with zip as the key function" - {
      "list input" - {
        def fetch(inputs: List[Int]) = Future.successful(inputs.map(_.toString))
        val source = Clump.sourceZip(fetch _)
        assertResult(source.get(1), Some("1"))
      }
      "extra params" - {
        "one" - {
          def fetch(param1: Int, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_.toString))
          val source = Clump.sourceZip(fetch _)
          assertResult(source.get(1, 2), Some("3"))
        }
        "two" - {
          def fetch(param1: Int, param2: String, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2))
          val source = Clump.sourceZip(fetch _)
          assertResult(source.get(1, "a", 2), Some("3a"))
        }
        "three" - {
          def fetch(param1: Int, param2: String, param3: List[String], inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2).map(_ + param3.fold("")(_ + _)))
          val source = Clump.sourceZip(fetch _)
          assertResult(source.get(1, "a", List("b", "c"), 2), Some("3abc"))
        }
        "four" - {
          def fetch(param1: Int, param2: String, param3: List[String], param4: Boolean, inputs: List[Int]) = Future.successful(inputs.map(_ + param1).map(_ + param2).map(_ + param3.fold("")(_ + _)).map(_ + s"-$param4"))
          val source = Clump.sourceZip(fetch _)
          assertResult(source.get(1, "a", List("b", "c"), true, 2), Some("3abc-true"))
        }
      }
    }

    "creates a clump source from a singly keyed fetch function" - {
      "single key input" - {
        def fetch(input: Int) = Future.successful(Set(input, input + 1, input + 2))

        val source = Clump.sourceSingle(fetch _)

        assertResult(source.get(1), Some(Set(1, 2, 3)))
        assertResult(source.get(2), Some(Set(2, 3, 4)))
        assertResult(source.get(List(1, 2)), Some(List(Set(1, 2, 3), Set(2, 3, 4))))
      }
      "extra params" - {
        "one" - {
          def fetch(param1: String, input: Int) = Future.successful(input + param1)
          val source = Clump.sourceSingle(fetch _)
          assertResult(source.get("a", 2), Some("2a"))
        }
        "two" - {
          def fetch(param1: String, param2: List[Int], input: Int) = Future.successful(input + param1 + param2.mkString)
          val source = Clump.sourceSingle(fetch _)
          assertResult(source.get("a", List(1, 2), 3), Some("3a12"))
        }
        "three" - {
          def fetch(param1: String, param2: List[Int], param3: Int, input: Int) = Future.successful(input + param1 + param2.mkString + param3)
          val source = Clump.sourceSingle(fetch _)
          assertResult(source.get("a", List(1, 2), 3, 4), Some("4a123"))
        }
        "four" - {
          def fetch(param1: String, param2: List[Int], param3: Int, param4: List[String], input: Int) = Future.successful(input + param1 + param2.mkString + param3 + param4.mkString)
          val source = Clump.sourceSingle(fetch _)
          assertResult(source.get("a", List(1, 2), 3, List("b", "c"), 4), Some("4a123bc"))
        }
      }
    }

    "creates a clump source from various input/ouput type fetch functions (ClumpSource.apply)" - {
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
        assertResult(source.get(List(1, 2)), Some(List("1", "2")))

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

    "creates a clump source from various input/ouput type fetch functions (ClumpSource.from)" - {

      def setToMap: Set[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }
      def listToMap: List[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }
      def iterableToMap: Iterable[Int] => Future[Map[Int, String]] = { inputs => Future.successful(inputs.map(input => (input, input.toString)).toMap) }

      def testSource(source: ClumpSource[Int, String]) =
        assertResult(source.get(List(1, 2)), Some(List("1", "2")))

      testSource(Clump.source(setToMap))
      testSource(Clump.source(listToMap))
      testSource(Clump.source(iterableToMap))
    }
  }
}