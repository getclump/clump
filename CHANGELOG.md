# 0.0.12 / 9-May-2015

* Generate artifact without the twitter-util dependency (https://github.com/getclump/clump/pull/91)

# 0.0.11 / 27-Apr-2015

* Add a couple inferred methods and aliases to clump api (https://github.com/getclump/clump/pull/88)
* Add scaladoc to all public methods (https://github.com/getclump/clump/pull/87)
* Use the Apache license (https://github.com/getclump/clump/pull/85)

# 0.0.10 / 23-Mar-2015

* `Clump.sourceFrom` renamed to `Clump.source` for consistency
* `Clump.source` must now be called with a partially-applied function (`Clump.source(fetch _)`)
* `Clump.source` now accepts fetch functions with up to 4 additional parameters

# 0.0.9 / 18-Feb-2015

* `Clump.traverse` now accepts any kind of collection as input
* `Clump.future` now accepts `Future[T]` and `Future[Option[T]]`

# 0.0.8 / 10-Feb-2015

* `Clump.sourceFrom` - support iterable output

# 0.0.7 / 06-Feb-2015

* accept any kind of collection for `clump.collect` and `clump.list`

# 0.0.6 / 04-Feb-2015

* first public release