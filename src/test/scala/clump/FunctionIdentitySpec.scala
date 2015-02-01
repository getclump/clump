package clump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FunctionIdentitySpec extends Spec {

  def function(string: String, int: Int) = 1

  "detects that the same function created two times is the same" >> {
    "without parameters from the outer scope" in {
      def test = function _
      FunctionIdentity(test) mustEqual FunctionIdentity(test)
    }
    "with parameters from the outer scope" in {
      val string = "test"
      def test = (int: Int) => function(string, int)
      FunctionIdentity(test) mustEqual FunctionIdentity(test)
    }
  }

  "detects that functions created in different places are different" >> {
    "without parameters from the outer scope" in {
      def test1 = function _
      def test2 = function _
      FunctionIdentity(test1) mustNotEqual FunctionIdentity(test2)
    }
    "with parameters from the outer scope" in {
      val string = "test"
      def test1 = (int: Int) => function(string, int)
      def test2 = (int: Int) => function(string, int)
      FunctionIdentity(test1) mustNotEqual FunctionIdentity(test2)
    }
  }

  "considers the values from the outer scope to generate the identity" >> {
    "equal" in {
      def test(string: String) = (int: Int) => function(string, int)
      val test1 = test("1")
      val test2 = test("1")
      FunctionIdentity(test1) mustEqual FunctionIdentity(test2)
    }
    "not equal" in {
      def test(string: String) = (int: Int) => function(string, int)
      val test1 = test("1")
      val test2 = test("2")
      FunctionIdentity(test1) mustNotEqual FunctionIdentity(test2)
    }
  }
}