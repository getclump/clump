Important
=========

This document is based on suppositions about the Twitter's Stitch library, since it was announced[[1]](https://www.youtube.com/watch?v=VVpmMfT8aYw)[[2]](https://www.youtube.com/watch?v=bmIxIslimVY) but isn't open sourced yet.

Introduction
============

Considering the absence of a timeline for the Stitch open sourcing, we decided to start the Clump project. The initial goal was to have a similar implementation, but the project evolved to provide an approach more adherent to some use-cases we have in mind. This document presents what we believe to be the main differences between Stitch and Clump.

1. Optional fetches
===================

Stitch fetch sources are defined using objects:

```scala
case object FetchTracks extends SeqGroup[Id, Track] {
	def run(ids: Seq[Id]): Future[Seq[Track]] = 
		tracksService.fetch(ids)
}
```

For each ```Id``` input, the ```SeqGroup.run``` must provide one ```Track``` instance. The signature doesn't effectively represent the cases where some ```Track``` instances may be missing from the ```tracksService```'s response. As an alternative, Stitch allows to define the response as optional:

```scala
case object FetchTracks extends SeqGroup[Id, Option[Track] {
	def run(ids: Seq[Id]): Future[Seq[Option[Track]]] = 
		tracksService.fetch(ids)
}
```

Basically, we can say that the Stitch's API is optimized for non-optional fetches. On the other hand, Clump is optimized for optional fetches:

```scala
val tracksSource = Clump.source(tracksService.fetch)(_.trackId)
```

The source instance applies the transformations to represent each fetch result as an ```Option``` by extracting the track ids. 

This means that the final clump value is also optional:

```scala
val clump = tracksSource.get(222)
val result: Future[Option[Track]] = clump.get
```

2. Composition
==============

Given the optimization to deal with optional fetches, the ```Clump``` compositions are also different from Stitch. They have semantics similar to the relational database's joins, where not found joined elements make the tuple be filtered-out.

```scala
val clump: Clump[(Track, User)]
	for {
		track <- tracksSource.get(111)
		user <- usersSource.get(track.creator)
	} yield (track, user)
```

In this example, if the track's creator isn't found, the final result will be None.

```scala
val future: Future[Option[(Track, User)]] = clump.get
val result: Option[(Track, User)] = Await.result(future)
require(result === None)
```

If a nested clump is expected to be optional, it is possible to use the ```optional``` method to have a behavior similar to an outer join.

```scala
val clump: Clump[(Track, Option[User])]
	for {
		track <- tracksSource.get(111)
		user <- usersSource.get(track.creator).optional
	} yield (track, user)
```

3. Filtering
============

The behavior introduced by the optional fetches compositions allows the definition of filtering conditions:

```scala
val clump: Clump[(Track, User)]
	for {
		track <- tracksSource.get(111) if(track.owner == currentUser)
		user <- usersSource.get(track.creator)
	} yield (track, user)
```

Filtering is most probably not supported by Stitch.

4. Sourcing
===========

Stitch uses the ```SeqGroup``` objects to define fetch sources:

```scala
case object FetchTracks extends SeqGroup[Id, Track] {
	def run(ids: Seq[Id]): Future[Seq[Track]] = 
		tracksService.fetch(ids)
}
```

If ```tracksService.fetch``` matches the ```run``` signature, the ```SeqGroup``` implementation is straightforward. If that isn't the case, the user must implement the input/output transformations.

Clump has built-in transformations to ease some common use cases.

If the fetch function returns a collection, it is possible to define a ```keyExtractor``` function to determine which one is the input key for each output:

```scala
val fetch: (List[Id]) => Future[List[Track]] = tracksService.fetch _
val tracksSource = Clump.source(fetch)(_.trackId)
```

Some services can return a ```Map[Input, Output]```. It is possible to use them directly with ```Clump.sourceFrom```:

```scala
val fetch: (List[Id]) => Future[Map[Id, User]] = usersService.fetch _
val usersSource = Clump.sourceFrom(fetch)
```

If the service has an interface similar to the Stitch's ```SeqGroup```, it is possible to match the inputs/outputs using a positional approach:


```scala
val fetch: (List[Id]) => Future[List[Playlist]] = playlistsService.fetch _
val playlistsSource = Clump.sourceZip(fetch)
```

5. Caching
==========

It is common to have an object graph where the same resource is used in multiple places. For instance, many items of a tracks list can have the same creator.

Each clump execution triggered by ```clump.get``` has an implicit cache that can avoid fetching the same resource multiple times. This mechanism seems to be not present in Stitch.

6. Execution model
==================

Stitch has an execution model capable of inspecting the AST definition and prepare an execution plan to allow batching. It also has logic to apply simplifications on the execution plan.

Clump has a simpler execution model that favors parallelism. Basically, it uses four steps:

1. Find the "roots" of the composition
2. Expand the composition starting from the roots and register the pending fetches
3. Flush all pending fetches in parallel for each source
4. If there are nested structures, go back to 2 using them as the roots. If not, return the clump's value.

7. Nested flatmaps
==================

The Stitch talk mentions a limitation regarding the execution plan when a ```flatMap``` is used. It is not clear if batching also occurs for nested ```flatMap``` compositions. The Clump's expansion based execution allows batching of calls even for this scenario.
