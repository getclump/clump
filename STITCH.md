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

Another alternative is keep the fetches as non-optional and catch the `NotFound` exception that Stitch produces for not found resources.

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

Filtering is also supported by Stitch, but as final result is non-optional it throws a `MatchError` for compositions that yield empty values.

4. Caching
==========

It is common to have an object graph where the same resource is used in multiple places. For instance, many items of a tracks list can have the same creator. Each clump execution triggered by ```clump.get``` has an implicit cache that can avoid fetching the same resource multiple times.

This mechanism is not present in Stitch, but the its execution model is able to group deep nested fetches and thus reduce the impact of the missing cache.

