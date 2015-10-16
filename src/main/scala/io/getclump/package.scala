package io

package object getclump {

  private[getclump]type Try[+T] = scala.util.Try[T]
  private[getclump] val Try = scala.util.Try
  private[getclump] val Success = scala.util.Success
  private[getclump] val Failure = scala.util.Failure

  private[getclump]type Promise[T] = scala.concurrent.Promise[T]
  private[getclump] val Promise = scala.concurrent.Promise

  private[getclump]type Future[+T] = scala.concurrent.Future[T]
  private[getclump] val Future = scala.concurrent.Future

  private[getclump] def awaitResult[T](future: Future[T]) =
    scala.concurrent.Await.result(future, scala.concurrent.duration.Duration.Inf)
}
