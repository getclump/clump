![Clump](https://raw.githubusercontent.com/getclump/clump/master/clump.png)
=========================
A library for expressive and efficient service composition

[![Build Status](https://img.shields.io/travis/getclump/clump.svg)](https://api.travis-ci.org/getclump/clump.svg?branch=master)
[![Codacy Badge](https://img.shields.io/codacy/5fd8030f7c494b739f6ec5c1963371a8.svg)](https://www.codacy.com/app/fwbrasil/clump)
[![codecov.io](https://img.shields.io/codecov/c/github/getclump/clump.svg)](http://codecov.io/github/getclump/clump?branch=master)
[![Join the chat at https://gitter.im/getclump/clump](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/getclump/clump?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

* [Introduction](#introduction) 
* [Getting started](#getting-started)
* [Usage](#usage)
  * [Example](#example)
  * [Sources](#sources)
  * [Constants](#constants)
  * [Composition](#composition)
  * [Execution](#execution)
  * [Composition behavior](#composition-behavior)
  * [Filtering](#filtering)
  * [Exception handling](#exception-handling)
* [Internals](#internals)
* [Known limitations](#known-limitations)
* [Acknowledgments](#acknowledgments)
* [Versioning](#versioning)
* [License](#license)

# Introduction #

## Summary ##

Clump is a __Scala__ library that addresses the problem of knitting together data from multiple sources in an elegant and efficient way.

In a typical microservice-powered system, it is common to find awkward wrangling code to facilitate manually bulk-fetching
dependent resources. Worse, this problem of batching is often accidentally overlooked, resulting in **n** calls to a micro-service instead of **1**.

Clump removes the need for the developer to even think about bulk-fetching, batching and retries, providing a powerful and composable interface for aggregating resources.

An example of batching fetches using futures without Clump:

```scala
tracksService.get(trackIds).flatMap { tracks =>
  val userIds = tracks.map(_.creator)
  usersService.get(userIds).map { users =>
    val userMap = userIds.zip(users).toMap
    tracks.map { track =>
      new EnrichedTrack(track, userMap(track.creator))
    }
  }
}
```

The same composition using Clump:

```scala
Clump.traverse(trackIds) { trackId =>
  trackSource.get(trackId).flatMap { track =>
    userSource.get(track.creator).map { user =>
      new EnrichedTrack(track, user)
    }
  }
}
```

Or expressed more elegantly with a for-comprehension:

```scala
Clump.traverse(trackIds) { trackId =>
  for {
    track <- trackSource.get(trackId)
    user <- userSource.get(track.creator)
  } yield {
    new EnrichedTrack(track, user)
  }
```

## Users ##

The following companies are running Clump in production:

<img src="https://upload.wikimedia.org/wikipedia/en/9/92/SoundCloud_logo.svg" width="150" height="100"/>

<img src="https://upload.wikimedia.org/wikipedia/commons/8/80/LinkedIn_Logo_2013.svg" width="150" height="100"/>

## Problem ##

The microservices architecture introduces many new challenges when dealing with complex systems. One of them is the high number of remote procedure calls and the cost associated to them. Among the techniques applied to amortize this cost, batching of requests has an important role. Instead of paying the price of one call for each interaction, many interactions are batched in only one call.

While batching introduces performance enhancements, it also introduces complexity to the codebase. The common approach is to extract as much information as possible about what needs to be fetched, perform the batched fetch and extract the individual values to compose the final result. The steps need to be repeated many times depending on how complex is the final structure.

An example of batching using futures:

```scala
tracksService.get(trackIds).flatMap { tracks =>
  val userIds = tracks.map(_.creator)
  usersService.get(userIds).map { users =>
    val userMap = userIds.zip(users).toMap
    tracks.map { track =>
      new EnrichedTrack(track, userMap(track.creator))
    }
  }
}
```

This example has only one level of nested resources. In a complex system, it is common to have several levels:

```
• timeline
  • track post
    • track
      • creator
  • track repost
    • track
      • creator
    • reposter
  • playlist post
    • playlist
      • track ids
      • creator
  • playlist repost
    • playlist
      • track ids
      • creator
    • reposter
  • comment
    • user
  • user follow
    • follower
    • followee
```

This structure can also be part of a bigger structure that includes the user's data for instance. Given this scenario, the code that is capable of batching requests in an optimal way is really complex and hard to maintain.

## Solution ##

The complexity comes mainly from declaring together **what** needs to be fetched and **how** it should be fetched. Clump offers an embedded Domain-Specific Language (DSL) that allows declaration of **what** needs to be fetched and an execution model that determines **how** the resources should be fetched.

The execution model applies three main optimizations:

1. Batch requests when it is possible;
2. Fetch from the multiple sources in parallel;
3. Avoid fetching the same resource multiple times by using a cache.

The DSL is based on a monadic interface similar to ```Future```. It is a Free Monad, that produces a nested series of transformations without starting the actual execution. This is the characteristic that allows triggering of the execution separately from the definition of what needs to be fetched.

The execution model leverages on Applicative Functors to express the independence of computations. It exposes only ```join``` to the user but makes use of other applicative operations internally. This means that even without the user specifying what is independent, the execution model can apply optimizations.

# Getting started #

To use clump, just add the dependency to the project's build configuration. There are two versions of the project:

1. `clump-scala`, that uses Scala Futures and doesn't have external dependencies.
2. `clump-twitter`, that uses Twitter Futures and has the dependency to `twitter-util`.

__Important__: Change ```x.x.x``` with the latest version listed by the [CHANGELOG.md](https://github.com/getclump/clump/blob/master/CHANGELOG.md) file.

SBT

```scala
libraryDependencies ++= Seq(
  "io.getclump" %% "clump-scala" % "x.x.x"
)
```

```scala
libraryDependencies ++= Seq(
  "io.getclump" %% "clump-twitter" % "x.x.x"
)
```

Maven

```xml
<dependency>
    <groupId>io.getclump</groupId>
    <artifactId>clump-scala</artifactId>
    <version>x.x.x</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.getclump</groupId>
    <artifactId>clump-twitter</artifactId>
    <version>x.x.x</version>
</dependency>
```

# Usage #

## Example ##

Example usage of Clump:

```scala
import io.getclump.Clump

// Creates sources using the batched interfaces
val tracksSource = Clump.source(tracksService.fetch _)(_.id)
val usersSource = Clump.source(usersService.fetch _)(_.id)

def renderTrackPosts(userId: Long) = {

  // Defines the clump
  val clump: Clump[List[EnrichedTrack]] = enrichedTrackPosts(userId)

  // Triggers execution
  val future: Future[Option[List[EnrichedTrack]]] = clump.get

  // Renders the response
  future.map {
    case Some(trackPosts) => render.json(trackPosts)
    case None             => render.notFound
  }
}

// Composes a clump with the user's track posts
def enrichedTrackPosts(userId: Long) =
  for {
    trackPosts <- Clump.future(timelineService.fetchTrackPosts(userId))
    enrichedTracks <- Clump.traverse(trackPosts)(enrichedTrack(_))
  } yield {
    enrichedTracks
  }

// Composes an enriched track clump
def enrichedTrack(trackId: Long) =
  for {
    track <- tracksSource.get(trackId)
    creator <- usersSource.get(track.creatorId)
  } yield {
    new EnrichedTrack(track, creator)
  }
```

The usage of ```renderTrackPosts``` produces only three remote procedure calls:

1. Fetch the track posts list (from ```timelineService```);
2. Fetch the metadata for all the tracks (from ```tracksService```);
3. Fetch the user metadata for all the tracks' creators (from ```usersService```).

The final result can be ```notFound``` because the user can be found or not.

## Sources ##

Sources represent the remote systems' batched interfaces. Clump offers some methods to create sources using different strategies.

### Map ###

The ```Clump.source``` method accepts a function that returns a ```Map``` with the values for the found inputs.

```scala
def fetch(ids: List[Int]): Future[Map[Int, User]] = ...

val usersSource = Clump.source(fetch _)

val userClump = usersSource.get(id)
```

### Collection with value-to-key function ###

The ```Clump.source``` method also accepts a function that may return a collection with less elements or in a different order than requested. In these cases, a function may be provided to match the found results back to their input keys.

```scala
def fetch(ids: Set[Int]): Future[Set[User]] = ...

val usersSource = Clump.source(fetch _)(_.id)

val userClump = usersSource.get(id)
```

### Map with Success and Failure ###

Sometimes sources can mark individual entries as failed, even though the entire fetch function succeeded. For these sources, a function can be provided that maps from id to a Try object that can be marked as either Success or Failure.

```scala
def fetch(ids: List[Int]): Future[Map[Int, Try[User]]] = ...

val usersSource = Clump.source(fetch _)

val userClump = usersClump.get(id)
```

The userClump will be either contain a User if the value for that id was Success(User), otherwise it will contain the exception in the Failure object for that id. As usual, if the id is not found in the map then the Clump will be undefined.

### List with zip function ###

The ```Clump.sourceZip``` methods accepts a function that produces a list of outputs for each provided input. The result must keep the same order as the inputs list.

```scala
def fetch(ids: List[Int]): Future[List[User]] = ...

val usersSource = Clump.sourceZip(fetch _)

val userClump = usersSource.get(id)
```

### Additional parameters ###

For the three sourcing options above, it is possible to create sources that have up to four additional parameters, with the resulting ```ClumpSource``` accepting each parameter and a singular input. There is a restriction that the inputs must be the last parameter of the fetch function.

```scala
def fetch(session: UserSession, ids: List[Int]): Future[List[User]] = ...

val usersSource = Clump.source(fetch _)(_.id)

val userClump = usersSource.get(session, id)
```

### Additional configurations ###

Some services have a limitation on how many resources can be fetched in a single request. It is possible to define this limit for each source instance:

```scala
val usersSource = Clump.source(fetch _).maxBatchSize(100)
```

The source instance can be also configured to automatically retry failed fetches by using the ```maxRetries``` method. It receives a partial function that defines the number of retries for each type of exception. The default number of retries is zero.

```scala
val usersSource = 
    Clump.source(fetch _).maxRetries {
      case e: SomeException => 10
    }
```

## Constants ##

It is possible to create Clump instances based on values.

From a value:

```scala
val clump: Clump[Int] = Clump.value(111)
```

From a future:

```scala
// This method is useful as a bridge between Clump and non-batched services.
val clump: Clump[Int] = Clump.future(counterService.currentValueFor(111))
```

It is possible to create a failed Clump instance:

```scala
val clump: Clump[Int] = Clump.exception(new NumberFormatException)
```

There is a shortcut for a constant empty Clump:

```scala
val clump: Clump[Int] = Clump.empty
```

## Composition ##

Clump has a monadic interface similar to ```Future```.

It is possible to apply a simple transformation by using ```map```:

```scala
val intClump: Clump = Clump.value(1)
val stringClump: Clump[String] = intClump.map(_.toString)
```

If the transformation results on another Clump instance, it is possible to use ```flatMap```:

```scala
val clump: Clump[(Track, User)] =
  tracksSource.get(trackId).flatMap { track =>
    usersSource.get(track.id).map { user =>
      (track, user)
    }
  }
```

The ```join``` method produces a Clump that has a tuple with the values of two Clump instances:

```scala
val clump: Clump[(User, List[Track])] =
    usersSource.get(userId).join(userTracksSource.get(userId))
```

There are also methods to deal with collections. Use ```collect``` to transform a collection of Clump instances into a single Clump:

```scala
val userClumps: List[Clump[User]] = userIds.map(usersSource.get(_))
val usersClump: Clump[List[User]] = Clump.collect(usersClump)
```

Instead of ```map``` and then ```collect```, it is possible to use the shortcut ```traverse```:

```scala
val usersClump: Clump[List[User]] = Clump.traverse(userIds)(usersSource.get(_))
```

It is possible to use for-comprehensions as syntactic sugar to avoid having to write the compositions:

```scala
val trackClump: Clump[EnrichedTrack] =
    for {
      track <- tracksSource.get(trackId)
      creator <- usersSource.get(track.creatorId)
    } yield {
      new EnrichedTrack(track, creator)
    }
```

## Execution ##

The creation of Clump instances doesn't trigger calls to the remote services. The only exception is when the code explicitly uses ```Clump.future``` to invoke a service.

To trigger execution, it is possible to use ```get```:

```scala
val trackClump: Clump[EnrichedTrack] = ...
val user: Future[Option[EnrichedTrack]] = trackClump.get
```

Clump assumes that the remote services can return less elements than requested. That's why the result is an ```Option```, since the input's result may be missing.

It is possible to define a default value by using ```getOrElse```:

```scala
val trackClump: Clump[EnrichedTrack] = ...
val user: Future[EnrichedTrack] = trackClump.getOrElse(unknownTrack)
```

If it is guaranteed that the underlying service will always return results for all fetched inputs, it is possible to use ```apply```, that throws a ```NotFoundException``` if the result is empty:

```scala
val trackClump: Clump[EnrichedTrack] = ...
val user1: Future[EnrichedTrack] = trackClump.apply()
val user2: Future[EnrichedTrack] = trackClump() // syntactic sugar
```

When a Clump instance has a collection, it is possible to use the ```list``` method. It returns an empty collection if the result is ```None```:

```scala
val usersClump: Clump[List[User]] = Clump.traverse(userIds)(usersSource.get(_))
val users: Future[List[User]] = usersClump.list
```

## Composition behavior ##

The composition of Clump instances takes in consideration that the sources may not return results for all requested inputs. It has a behavior similar to the relational databases' joins, where not found joined elements make the tuple be filtered-out.

```scala
val clump: Clump[(Track, User)]
  for {
    track <- tracksSource.get(111)
    user <- usersSource.get(track.creator)
  } yield (track, user)
```

In this example, if the track's creator isn't found, the final result will be ```None```.

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

Another alternative is to define a fallback by using ```orElse```:

```scala
val clump: Clump[(Track, Option[User])]
  for {
    track <- tracksSource.get(111)
    user <- usersSource.get(track.creator).orElse(usersSource.get(track.uploader))
  } yield (track, user)
```

## Filtering ##

The behavior introduced by the optional fetch compositions allows defining of filtering conditions:

```scala
val clump: Clump[(Track, User)]
  for {
    track <- tracksSource.get(111) if(track.owner == currentUser)
    user <- usersSource.get(track.creator)
  } yield (track, user)
```

## Exception handling ##

Clump offers some mechanisms to deal with failed fetches.

The ```handle``` method defines a fallback value given an exception:

```scala
val clump: Clump[User] =
    usersService.get(userId).handle {
      case _: SomeException =>
        defaultUser
    }
```

If the fallback value is another Clump instance, it is possible to use ```rescue```:

```scala
val clump: Clump[User] =
    usersService.get(trackCreatorId).rescue {
      case _: SomeException =>
        usersService.get(trackUploaderId)
    }
```

# Internals #

This section explains how Clump works under the hood.

The codebase is relatively small. The only type explicitly exposed to the user is ```Clump```, but internally there are four in total:

[Clump](https://github.com/getclump/clump/blob/master/src/main/scala/io/getclump/Clump.scala) - Defines the public interface of Clump and represents the abstract syntactic tree (AST) for the compositions.

[ClumpSource](https://github.com/getclump/clump/blob/master/src/main/scala/io/getclump/ClumpSource.scala) - Represents the external systems' batched interfaces.

[ClumpFetcher](https://github.com/getclump/clump/blob/master/src/main/scala/io/getclump/ClumpFetcher.scala) - It has the logic to fetch from a ```ClumpSource```, maintains the implicit cache and implements the logic to retry failed fetches.

[ClumpContext](https://github.com/getclump/clump/blob/master/src/main/scala/io/getclump/ClumpContext.scala) - It is the execution model engine created automatically for each execution. It keeps the state by using a collection of ```ClumpFetcher```s.

Take some time to read the code of these classes. It will help to have a broader view and understand the explanation that follows.

Lets see what happens when this example is executed:

```scala
val usersSource = Clump.source(usersService.fetch _)(_.id)
val tracksSource = Clump.source(tracksService.fetch _)(_.id)

val clump: Clump[List[EnrichedTrack]] =
    Clump.traverse(trackIds) { trackId =>
      for {
        track <- tracksSource.get(trackId)
        user <- usersSource.get(track.creatorId)
      } yield {
        new EnrichedTrack(track, user)
      }
    }

val tracks: Future[List[Track]] = clump.list
```

## Sources creation

```scala
val usersSource = Clump.source(usersService.fetch _)(_.id)
val tracksSource = Clump.source(tracksService.fetch _)(_.id)
```

The ```ClumpSource``` instances are created using one of the shortcuts that the ```Clump``` object provides. They don't hold any state and allow to create Clump instances representing the fetch. Clump uses the source's identity to group requests and perform batched fetches, so it is __not__ possible to have multiple instances of the same source within a clump composition and execution.

## Composition

```scala
val clump: Clump[List[EnrichedTrack]] =
    Clump.traverse(trackIds) { trackId =>
      ...
    }
```

The ```traverse``` method is used as a shortcut for ```map``` and then ```collect```, so this code could be rewritten as follows:

```scala
val clump: Clump[List[EnrichedTrack]] =
    Clump.collect(trackIds.map { trackId => 
      ...
    }
```

For each ```trackId```, a for-comprehension is used to compose a Clump that has the ```EnrichedTrack```:

```scala
      for {
        track <- tracksSource.get(trackId)
        user <- usersSource.get(track.creatorId)
      } yield {
        new EnrichedTrack(track, user)
      }
```

The for-comprehension is actually just syntactic sugar using ```map``` and ```flatMap```, so this code is equivalent to:

```scala
      tracksSource.get(trackId).flatMap { track =>
        usersSource.get(track.creatorId).map { user =>
          new EnrichedTrack(track, user)
        }
      }
```

There are three methods being used in this composition:

1. ```get``` creates a ```ClumpFetch``` instances that is the AST element representing the fetch. It doesn't trigger the actual fetch, only uses the ```ClumpFetcher``` instance to produce a ```Future``` that will be executed by the ```ClumpContext``` when the execution is triggered.

2. ```flatMap``` creates a ```ClumpFlatMap``` instance representing the operation. It just composes a new future that is based on the result of the initial Clump and the result of the nested Clump.

3. ```map``` creates a ```ClumpMap``` instance representing the map operation. It composes a new future by applying the specified transformation.

## Execution

Now comes the most important part. Until now, the compositions only create ```Clump*``` instances to represent the operations and produce futures that will be fulfilled when the execution is triggered. You probably have noticed that the Clump instances define three things:

1. ```result``` that has the ```Future``` result for the operation
2. ```upstream``` that returns the upstream Clump instances that were used as the basis for the composition
3. ```downstream``` that returns the downstream Clump instances created as a result of the operation

Note that ```downstream``` returns a ```Future[List[Clump[_]]]```, while ```upstream``` returns a ```List[Clump[_]]``` directly. This happens because ```downstream``` produces Clump instances that are available only after the ```upstream``` execution.

These methods are used by the ```ClumpContext``` to apply the execution model. It has a collection with all ```ClumpFetcher``` instances in the composition.

This is the code that triggers the execution:

```scala
val tracks: Future[List[Track]] = clump.list
```

The ```list``` method is just a shortcut to ease getting the value of Clump instances that have a collection. The actual execution is triggered by the ```get``` method. It flushes the context and returns the Clump's result.

The context flush is a mutually recursive function that uses the following steps:

* Retrieve a list of visible Clump instances. Recall  that ```upstream``` returns a ```List[Clump[_]]``` directly. All the visible Clump instances are therefore retrieved by recursively getting a list of upstream Clumps, then all the upstream Clumps from each Clump in that list, and so on.
* Perform all the fetches among the visible Clump instances. This batches together calls to the same ```ClumpSource``` and also performs all batch flushes in parallel.
* Finally, flush the downstream instances
  * The rule is that all upstream instances must be flushed before any of the downstream instances. The only Clump instances that fulfill this requirement at this point are the downstream instances of the Clumps at the deepest level of the visible traversal. Flush these first.
  * As each level of downstream instances is flushed, move up a level and flush again since the pre-requisite must be fulfilled. (The upstream have already been flushed in the first step, and any downstream Clumps from the current Clump's upstream instances would have been at a deeper level of composition and therefore have been flushed already).

You could consider this a upstream-first traversal of the Clump graph.

In case you are wondering why we need this upstream mechanism since we have the Clump instance at hand and could start the execution from it: actually the instance used to trigger the execution isn't the "root" of the composition. For instance:

```scala
val clump: Clump[EnrichedTrack] =
    tracksSource.get(trackId).flatMap { track =>
      usersSource.get(track.creatorId).map { user =>
        new EnrichedTrack(track, user)
      }
    }
```

The clump instance will be a ```ClumpFlatMap```, not the ```ClumpFetch``` created by the ```tracksSource.get(trackId)``` call. This is the AST behind the clump instance:

```
                                   +----------> Empty                    
                                   |Up                                   
                            +------+-----+                               
                            | ClumpFetch |                               
               +----------> |  (line 2)  |                               
               |Up          +------+-----+                               
               |                   |Down                                 
               |                   +----------> Empty                    
       +-------+------+                                                  
       | ClumpFlatMap |                                                  
get--> |   (line 2)   |                                   +-------> Empty
       +-------+------+                                   |Up            
               |                                +---------+--+           
               |                   +----------> | ClumpFetch |           
               |                   |Up          |  (line 3)  |           
               |Down        +------+-----+      +---------+--+           
               +----------> |  ClumpMap  |                |Down          
                            |  (line 3)  |                +-------> Empty
                            +------+-----+                               
                                   |Down                                 
                                   +----------> Empty                    
```

The steps to execute this composition happen as follows:

* The execution is triggered by the ```get``` method on the ```ClumpFlatMap``` instance
* The only visible Clumps are ```ClumpFlatMap``` and ```ClumpFetch```
* The fetch is executed
* Downstream instances are flushed starting at the deepest level
  * Downstream of ```ClumpFetch``` is empty and returns immediately
  * Downstream of ```ClumpFlatMap``` can now be flushed because the entire upstream path was already flushed
    * Visibility has increased, so now the visible Clumps are ```ClumpMap``` and ```ClumpFetch```
    * The fetch is executed
    * Downstream instances are flushed starting at the deepest level
      * Downstream of ```ClumpFetch``` is empty and returns immediately
      * Downstream of ```ClumpMap``` is empty and returns immediately

Note that this example has only one Clump instance per flush phase, but normally there are multiple instances. This is what allows Clump to batch requests that are in the same flush phase.

# Known limitations #

The execution model is capable of batching requests that are in the same level of the composition. For instance, this example produces only one fetch from ```usersSource```:

```scala
val clump: Clump[List[EnrichedTrack]] =
    Clump.traverse(trackIds) { trackId =>
      for {
        track <- tracksSource.get(trackId)
        user <- usersSource.get(track.creatorId)
      } yield {
        new EnrichedTrack(track, user)
      }
    }
```

The next example has two fetches from ```usersSource```: one for the playlists' creators and other for the tracks' creators.

```scala
val clump: Clump[List[EnrichedPlaylist]] =
    Clump.traverse(playlistIds) { playlistId =>
      for {
        playlist <- playlistsSource.get(playlistId)
        creator <- usersSource.get(playlist.creator)
        tracks <- 
          Clump.traverse(playlist.trackids) {
            for {
              track <- tracksSource.get(trackId)
              user <- usersSource.get(track.creatorId)
            } yield {
              new EnrichedTrack(track, user)
            }
          }
      } yield {
        new EnrichedPlaylist(playlist, creator, tracks)
      }
    }
```

Considering that they happen in different levels of the composition, the execution model will execute two batched fetches to ```usersSource```, not one. This limitation is alleviated by the implicit caching if the playlist and tracks have the same creator.

# Acknowledgments #

Clump was inspired by the Twitter's Stitch project. The initial goal was to have a similar implementation, but the project evolved to provide an approach more adherent to some use-cases we have in mind. See [STITCH.md](https://github.com/getclump/clump/blob/master/STITCH.md) for more information about the differences between Stich and Clump.

Facebook's [Haxl paper](http://community.haskell.org/~simonmar/papers/haxl-icfp14.pdf) and the Futurice's [blog post](http://futurice.com/blog/an-example-of-functional-design) about Jobba also were important sources for the development phase.

The project was initially built using SoundCloud's [Hacker Time](https://developers.soundcloud.com/blog/stop-hacker-time).

# Versioning #

Clump adheres to Semantic Versioning 2.0.0. If there is a violation of this scheme, report it as a bug. Specifically, if a patch or minor version is released and breaks backward compatibility, that version should be immediately yanked and/or a new version should be immediately released that restores compatibility. Any change that breaks the public API will only be introduced at a major-version release.

# License #

See the [LICENSE](https://github.com/getclump/clump/blob/master/LICENSE.txt) file for details.
