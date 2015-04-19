package io.getclump

import scala.concurrent.ExecutionContext
import scala.concurrent.{Future => ScalaFuture}
import scala.concurrent.{Promise => ScalaPromise}
import scala.util.Failure
import scala.util.Success

import com.twitter.util.{Future => TwitterFuture}
import com.twitter.util.{Promise => TwitterPromise}

/**
 * Provides bidirectional implicit conversions between Twitter Futures and Scala Futures
 *
 * Usage:
 * {{{
 *   import io.getclump.FutureBridge._
 * }}}
 */
object FutureBridge {

  /**
   * Convert a Twitter Future to a Scala Future
   */
  implicit def twitterToScala[T](future: TwitterFuture[T]): ScalaFuture[T] = {
    val promise = ScalaPromise[T]()
    future.onSuccess(promise.success)
    future.onFailure(promise.failure)
    promise.future
  }

  /**
   * Convert a Scala Future to a Twitter Future given an execution context
   */
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
