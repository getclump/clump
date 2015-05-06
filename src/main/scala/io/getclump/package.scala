package io

package object getclump {

  private[getclump] implicit val executionContext = scala.concurrent.ExecutionContext.global

  private[getclump]type Promise[T] = scala.concurrent.Promise[T]
  private[getclump] val Promise = scala.concurrent.Promise

  private[getclump]type Future[+T] = scala.concurrent.Future[T]
  private[getclump] val Future = scala.concurrent.Future

  def blockOn[T](future: Future[T]) =
    scala.concurrent.Await.result(future, scala.concurrent.duration.Duration.Inf)
}

//package object getclump {
//
//  private[getclump]type Promise[T] = com.twitter.util.Promise[T]
//  private[getclump] val Promise = com.twitter.util.Promise
//
//  private[getclump]type Future[+T] = com.twitter.util.Future[T]
//  private[getclump] val Future = com.twitter.util.Future
//
//  implicit class PromiseBridge[T](val promise: Promise[T]) extends AnyVal {
//    def complete(result: com.twitter.util.Try[T]) =
//      promise.update(result)
//  }
//
//  implicit class FutureBridge[T](val future: Future[T]) extends AnyVal {
//    def recover[U >: T](pf: PartialFunction[Throwable, U]) = future.handle(pf)
//    def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]]) = future.rescue(pf)
//    def zip[U](that: Future[U]) = future.join(that)
//    def isCompleted = future.poll.isDefined
//    def onComplete[U](f: com.twitter.util.Try[T] => U) = future.liftToTry.map(f)
//  }
//
//  implicit class FutureCompanionBridge(val companion: Future.type) extends AnyVal {
//    def successful[T](value: T) = Future.value(value)
//    def failed[T](exception: Throwable) = Future.exception[T](exception)
//    def sequence[T](futures: Seq[Future[T]]) = Future.collect(futures)
//  }
//
//  def blockOn[T](future: Future[T]) =
//    com.twitter.util.Await.result(future)
//}