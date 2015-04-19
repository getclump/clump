package io.getclump

import scala.annotation.implicitNotFound
import scala.collection.mutable.HashMap

import com.twitter.util.Future
import com.twitter.util.Local

@implicitNotFound("Cannot find an implicit ClumpContext, either import io.getclump.ClumpContext.Implicits.default to get from ThreadLocal or use a custom one")
private[getclump] final class ClumpContext {

  private[this] val fetchers =
    new HashMap[FunctionIdentity, ClumpFetcher[_, _]]()

  def fetcherFor[T, U](source: ClumpSource[T, U]): ClumpFetcher[T, U] =
    synchronized {
      fetchers
        .getOrElseUpdate(source.functionIdentity, new ClumpFetcher(source))
        .asInstanceOf[ClumpFetcher[T, U]]
    }

  def flush(clumps: List[Clump[_]]): Future[Unit] =
    clumps match {
      case Nil => Future.Unit
      case _ =>
        flushUpstream(clumps).flatMap { _ =>
          flushFetches(clumps).flatMap { _ =>
            flushDownstream(clumps)
          }
        }
    }

  private[this] def flushUpstream(clumps: List[Clump[_]]) =
    flush(clumps.map(_.upstream).flatten)

  private[this] def flushDownstream(clumps: List[Clump[_]]) =
    Future.collect(clumps.map(_.downstream)).flatMap { down =>
      flush(down.flatten.toList)
    }

  private[this] def flushFetches(clumps: List[Clump[_]]) =
    Future.collect(fetchersFor(clumps).map(_.flush))

  private[this] def fetchersFor(clumps: List[Clump[_]]) =
    clumps.collect {
      case clump: ClumpFetch[_, _] => clump.fetcher
    }.distinct
}

object ClumpContext {

  private[this] val local = new Local[ClumpContext]

  def default: ClumpContext = Implicits.default

  object Implicits {
    implicit def default: ClumpContext =
      local().getOrElse {
        val context = new ClumpContext
        local.set(Some(context))
        context
      }
  }
}
