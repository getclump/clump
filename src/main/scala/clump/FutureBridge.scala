package clump

import scala.concurrent.{ExecutionContext, Future => ScalaFuture, Promise => ScalaPromise}
import scala.util.{ Failure, Success }

import com.twitter.util.{Future => TwitterFuture, Promise => TwitterPromise}

object FutureBridge {

  implicit def twitterToScala[T](future: TwitterFuture[T]): ScalaFuture[T] = {
    val promise = ScalaPromise[T]()
    future.onSuccess(promise.success)
    future.onFailure(promise.failure)
    promise.future
  }

  implicit def scalaToTwitter[T](future: ScalaFuture[T])(implicit ctx: ExecutionContext): TwitterFuture[T] = {
    val promise = TwitterPromise[T]()
    future.onComplete {
      case Success(result) =>
        promise.setValue(result)
      case Failure(exception) =>
        promise.setException(exception)
    }
    promise
  }
}
