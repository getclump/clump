# 1.1.0 / 30-Oct-2015

* Added .sourceTry method to allow individual results to be marked as failed in a batch fetch (https://github.com/getclump/clump/pull/109)
* Add docs to Clump and ClumpSource files (https://github.com/getclump/clump/pull/110)

# 1.0.0 / 21-Sep-2015

* 1.0.0 Release

# 0.0.20 / 15-Sep-2015

* Fix batching for nested flatmaps at different levels of composition (https://github.com/getclump/clump/pull/108)

# 0.0.19 / 7-Sep-2015

* Simplify API to no longer expose internal Option (https://github.com/getclump/clump/pull/106)

# 0.0.18 / 4-Sep-2015

* Fix issue with CanBuildFrom and collections with metadata (https://github.com/getclump/clump/pull/105)

# 0.0.17 / 22-Aug-2015

* Allow custom execution context to be passed in (https://github.com/getclump/clump/pull/104)

# 0.0.16 / 21-Aug-2015

* Add sequence alias for collect, custom NoSuchElementException message (https://github.com/getclump/clump/pull/102)

# 0.0.15 / 23-Jul-2015

* Optimize ClumpContext execution algorithm to fetch in parallel at different levels of composition (https://github.com/getclump/clump/pull/99)

# 0.0.14 / 19-Jun-2015

* Set default max batch size of 100 for `ClumpSource` (https://github.com/getclump/clump/pull/95)
* Add `Clump.sourceSingle` for creating a clump source for an endpoint that doesn't support bulk fetches. (https://github.com/getclump/clump/pull/93)

# 0.0.13 / 31-May-2015

* `ClumpFetch` now honors original `ClumpSource` call order when fetching from underlying source (https://github.com/getclump/clump/pull/92)

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
