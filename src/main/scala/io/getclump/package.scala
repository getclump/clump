package io

package object getclump {
  
  private [getclump] type Promise[T] = com.twitter.util.Promise[T]
  private [getclump] val Promise = com.twitter.util.Promise
  
  private [getclump] type Future[+T] = com.twitter.util.Future[T]
  private [getclump] val Future = com.twitter.util.Future
}