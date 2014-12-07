package clump

import com.twitter.util.Local
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap

class ClumpContext {

  private val fetchers = new HashMap[ClumpSource[_, _], ClumpFetcher[_, _]]()

  def fetcherFor[T, U](source: ClumpSource[T, U]) =
    synchronized {
      fetchers
        .getOrElseUpdate(source, new ClumpFetcher(source))
        .asInstanceOf[ClumpFetcher[T, U]]
    }
}

object ClumpContext {

  private val local = new Local[ClumpContext]

  def apply() =
    local().getOrElse {
      val context = new ClumpContext
      local.set(Some(context))
      context
    }
}
