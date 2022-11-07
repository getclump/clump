package io.getclump

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import java.time.Instant
import java.util.Date

@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Spec {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository
  val zipUserRepository = new ZipUserRepository
  val filteredUserRepository = new FilteredUserRepository
  val timelineRepository = new TimelineRepository
  val likeRepository = new LikeRepository
  val trackRepository = new TrackRepository
  val topTracksRepository = new TopTracksRepository
  val failingTweetRepository = new FailingTweetRepository

  val tweets = Clump.source(tweetRepository.tweetsFor _)
  val users = Clump.source(userRepository.usersFor _)(_.userId)
  val filteredUsers = Clump.source(filteredUserRepository.usersFor _)(_.userId)
  val zippedUsers = Clump.sourceZip(zipUserRepository.usersFor _)
  val timelines = Clump.source(timelineRepository.timelinesFor _)(_.timelineId)
  val likes = Clump.source(likeRepository.likesFor _)(_.likeId)
  val tracks = Clump.source(trackRepository.tracksFor _)(_.trackId)

  val timelineService = new TimelineService
  val tracksService = new TrackRepository
  val playlistsService = new PlaylistsService
  val playStatsService = new PlayStatsService
  val usersService = new UserRepository
  val likesService = new LikesService
  val commentsService = new CommentsService

  val tracksSource = Clump.source(tracksService.tracksFor _)(_.trackId)
  val playStatsSource = Clump.source(playStatsService.forTracks _)(_.trackId)
  val usersSource = Clump.source(usersService.usersFor _)(_.userId)
  val playlistsSource = Clump.source(playlistsService.playlistsFor _)(_.playlistId)
  val likesSource = Clump.source(likesService.forEntities _)(_.entityId)
  val commentsSource = Clump.source(commentsService.forTracks _)(_.trackId)
  val timelineSource = Clump.sourceSingle(timelineService.postsFor _)

  "A Clump should batch calls to services" in {
    val tweetRepositoryMock = mock[TweetRepository]
    val tweets = Clump.source(tweetRepositoryMock.tweetsFor _)

    val userRepositoryMock = mock[UserRepository]
    val users = Clump.source(userRepositoryMock.usersFor _)(_.userId)
    val topTracks = Clump.sourceSingle(topTracksRepository.topTracksFor _)

    tweetRepositoryMock.tweetsFor(Set(1L, 2L, 3L)) returns
      Future.successful(Map(
        1L -> Tweet("Tweet1", 10),
        2L -> Tweet("Tweet2", 20),
        3L -> Tweet("Tweet3", 30)
      ))

    userRepositoryMock.usersFor(Set(10L, 20L, 30L)) returns
      Future.successful(Set(
        User(10, "User10"),
        User(20, "User20"),
        User(30, "User30")
      ))

    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- users.get(tweet.userId)
        tracks <- topTracks.get(user)
      } yield (tweet, user, tracks)
    }

    awaitResult(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10"), Set(Track(10, "Track10"), Track(11, "Track11"), Track(12, "Track12"))),
      (Tweet("Tweet2", 20), User(20, "User20"), Set(Track(20, "Track20"), Track(21, "Track21"), Track(22, "Track22"))),
      (Tweet("Tweet3", 30), User(30, "User30"), Set(Track(30, "Track30"), Track(31, "Track31"), Track(32, "Track32")))))
  }

  "A Clump should batch calls to parameterized services" in {
    val parameterizedTweetRepositoryMock = mock[ParameterizedTweetRepository]
    val tweets = Clump.source(parameterizedTweetRepositoryMock.tweetsFor _)

    val parameterizedUserRepositoryMock = mock[ParameterizedUserRepository]
    val users = Clump.source(parameterizedUserRepositoryMock.usersFor _)(_.userId)

    parameterizedTweetRepositoryMock.tweetsFor("foo", Set(1, 2, 3)) returns
      Future.successful(Map(
        1L -> Tweet("Tweet1", 10),
        2L -> Tweet("Tweet2", 20),
        3L -> Tweet("Tweet3", 30)
      ))

    parameterizedUserRepositoryMock.usersFor("bar", Set(10, 20, 30)) returns
      Future.successful(Set(
        User(10, "User10"),
        User(20, "User20"),
        User(30, "User30")
      ))

    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get("foo", tweetId)
        user <- users.get("bar", tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "it should be able to be used in complex nested fetches" in {
    val timelineIds = List(1, 3)
    val enrichedTimelines = Clump.traverse(timelineIds) { id =>
        for {
          timeline <- timelines.get(id)
          enrichedLikes <- Clump.traverse(timeline.likeIds) { id =>
            for {
              like <- likes.get(id)
              (tracks, users) <- tracks.get(like.trackIds).join(users.get(like.userIds))
            } yield (like, tracks, users)
          }
        } yield (timeline, enrichedLikes)
      }

    awaitResult(enrichedTimelines.get) ==== Some(List(
      (Timeline(1, List(10, 20)), List(
        (Like(10, List(100, 200), List(1000, 2000)), List(Track(100, "Track100"), Track(200, "Track200")), List(User(1000, "User1000"), User(2000, "User2000"))),
        (Like(20, List(200, 400), List(2000, 4000)), List(Track(200, "Track200"), Track(400, "Track400")), List(User(2000, "User2000"), User(4000, "User4000"))))),
      (Timeline(3, List(30, 60)), List(
        (Like(30, List(300, 600), List(3000, 6000)), List(Track(300, "Track300"), Track(600, "Track600")), List(User(3000, "User3000"), User(6000, "User6000"))),
        (Like(60, List(600, 1200), List(6000, 12000)), List(Track(600, "Track600"), Track(1200, "Track1200")), List(User(6000, "User6000"), User(12000, "User12000")))))))
  }

  "it should be usable with regular maps and flatMaps" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Clump[List[(Tweet, User)]] =
      Clump.traverse(tweetIds) { tweetId =>
        tweets.get(tweetId).flatMap(tweet =>
          users.get(tweet.userId).map(user => (tweet, user)))
      }

    awaitResult(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "it should allow unwrapping Clumped lists with clump.list" in {
    val enrichedTweets: Clump[List[(Tweet, User)]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- users.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.list) ==== List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30")))
  }

  "it should work with Clump.sourceZip" in {
    val enrichedTweets = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- zippedUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "A Clump can have a partial result" in {
    val onlyFullObjectGraph: Clump[List[(Tweet, User)]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- filteredUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    awaitResult(onlyFullObjectGraph.get) ==== Some(List((Tweet("Tweet2", 20), User(20, "User20"))))

    val partialResponses: Clump[List[(Tweet, Option[User])]] = Clump.traverse(1, 2, 3) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- filteredUsers.get(tweet.userId).optional
      } yield (tweet, user)
    }

    awaitResult(partialResponses.get) ==== Some(List(
      (Tweet("Tweet1", 10), None),
      (Tweet("Tweet2", 20), Some(User(20, "User20"))),
      (Tweet("Tweet3", 30), None)))
  }

  "A Clump can have individual failing entities" in {
    val source = Clump.sourceTry(failingTweetRepository.tweetsFor _)

    def getTweetWithoutFallback(id: Long) = source.get(id)
    def getTweetWithFallback(id: Long) = source.get(id).fallback(Tweet("<error>", 0))

    val fail = for {
      tweetA <- getTweetWithoutFallback(1)
      tweetB <- getTweetWithoutFallback(-1)
    } yield (tweetA, tweetB)

    awaitResult(fail.get) must throwA[IllegalStateException]

    val success = for {
      tweetA <- getTweetWithFallback(1)
      tweetB <- getTweetWithFallback(-1)
    } yield (tweetA, tweetB)

    awaitResult(success.get) ==== Some((Tweet("Tweet1", 10), Tweet("<error>", 0)))
  }

  "Soundcloud extended timeline example" in {
    val currentUserId = 123L
    val enrichedPosts = timelineSource.get(currentUserId).flatMap { posts => Clump.collect(posts.map {
      case TrackPost(trackId, timestamp) => for {
        (track, trackComments, likes, stats) <- Clump.join(
          tracksSource.get(trackId),
          commentsSource.get(trackId),
          likesSource.get(trackId),
          playStatsSource.get(trackId)
        )
        creator <- usersSource.get(track.creatorId)
        enrichedComments <- Clump.traverse(trackComments.comments) { comment =>
          for {
            commenter <- usersSource.get(comment.commenterId)
          } yield {
            EnrichedComment(comment.text, commenter.name, comment.timestamp)
          }
        }
      } yield {
        EnrichedTrackPost(EnrichedTrack(track, creator, stats.playCount, likes.likeCount, enrichedComments), timestamp)
      }
      case PlaylistPost(playlistId, timestamp) => for {
        (playlist, likes) <- playlistsSource.get(playlistId) join likesSource.get(playlistId)
        curator <- usersSource.get(playlist.curatorId)
        enrichedTracks <- Clump.traverse(playlist.trackIds) { trackId =>
          for {
            (track, stats) <- tracksSource.get(trackId) join playStatsSource.get(trackId)
            creator <- usersSource.get(track.creatorId)
          } yield {
            EnrichedTrack(track, creator, stats.playCount)
          }
        }
      } yield {
        EnrichedPlaylistPost(EnrichedPlaylist(enrichedTracks, curator, likes.likeCount, playlist.name), timestamp)
      }
    })}
    awaitResult(enrichedPosts.get) ==== null
  }

  "Soundcloud extended timeline example without clump" in {
    // timeline posts
    // playlists
    // tracks
    // comments (track post tracks)
    // stats (all tracks)
    // likes (playlists, all tracks)
    // users (playlists, tracks and comments)
    val currentUserId = 123L
    val enrichedPosts = for {
      posts <- timelineService.postsFor(currentUserId)
      playlistIds = posts.map { case PlaylistPost(playlistId, _) => playlistId }.toSet
      trackPostTrackIds = posts.map { case TrackPost(trackId, _) => trackId }.toSet
      (playlists, comments) <- playlistsService.playlistsFor(playlistIds) zip
        commentsService.forTracks(trackPostTrackIds)
      playlistTrackIds = playlists.flatMap(_.trackIds)
      allTrackIds = playlistTrackIds union trackPostTrackIds
      allEntityIds = allTrackIds union playlistIds
      ((tracks, stats), likes) <- tracksService.tracksFor(allTrackIds) zip
        playStatsService.forTracks(trackPostTrackIds) zip
        likesService.forEntities(allEntityIds)
      allUserIds = comments.flatMap(_.comments.map(_.commenterId)) union
        playlists.map(_.curatorId) union tracks.map(_.creatorId)
      users <- usersService.usersFor(allUserIds)
    } yield {
      val playlistIdMap = playlists.map { playlist => (playlist.playlistId, playlist) }.toMap
      val trackIdMap = tracks.map { track => (track.trackId, track) }.toMap
      val userIdMap = users.map { user => (user.userId, user) }.toMap
      val statsIdMap = stats.map { stat => (stat.trackId, stat) }.toMap
      val likesIdMap = likes.map { like => (like.entityId, like) }.toMap
      val commentsIdMap = comments.map { comment => (comment.trackId, comment) }.toMap
      posts.map {
        case TrackPost(trackId, timestamp) =>
          val track = trackIdMap(trackId)
          val creator = userIdMap(track.creatorId)
          val stats = statsIdMap(trackId)
          val likes = likesIdMap(trackId)
          val comments = commentsIdMap(trackId).comments.map { comment =>
            EnrichedComment(comment.text, userIdMap(comment.commenterId).name, comment.timestamp)
          }
          EnrichedTrackPost(EnrichedTrack(track, creator, stats.playCount, likes.likeCount, comments), timestamp)
        case PlaylistPost(playlistId, timestamp) =>
          val playlist = playlistIdMap(playlistId)
          val likes = likesIdMap(playlistId)
          val tracks = playlist.trackIds.map { trackIdMap(_) }.map { track =>
            EnrichedTrack(track, userIdMap(track.creatorId), statsIdMap(track.trackId).playCount)
          }
          EnrichedPlaylistPost(
            EnrichedPlaylist(tracks, userIdMap(playlist.curatorId), likes.likeCount, playlist.name), timestamp
          )
      }
    }
    awaitResult(enrichedPosts) ==== null
  }
}

case class EnrichedComment(text: String, name: String, timestamp: Instant)
case class EnrichedTrack(track: Track, creator: User, plays: Int, likes: Int = 0, comments: List[EnrichedComment] = List())
case class EnrichedPlaylist(tracks: List[EnrichedTrack], curator: User, likes: Int, name: String)
sealed trait EnrichedPost
case class EnrichedTrackPost(track: EnrichedTrack, timestamp: Instant) extends EnrichedPost
case class EnrichedPlaylistPost(playlist: EnrichedPlaylist, timestamp: Instant) extends EnrichedPost

case class Tweet(body: String, userId: Long)

case class User(userId: Long, name: String)

case class Timeline(timelineId: Int, likeIds: List[Long])

case class Like(likeId: Long, trackIds: List[Long], userIds: List[Long])

case class Track(trackId: Long, name: String, creatorId: Long = 6L)

class TweetRepository {
  def tweetsFor(ids: Set[Long]): Future[Map[Long, Tweet]] = {
    Future.successful(ids.map(id => id -> Tweet(s"Tweet$id", id * 10)).toMap)
  }
}

trait ParameterizedTweetRepository {
  def tweetsFor(prefix: String, ids: Set[Long]): Future[Map[Long, Tweet]]
}

class FailingTweetRepository {
  def tweetsFor(ids: Set[Long]): Future[Map[Long, Try[Tweet]]] = {
    Future.successful(ids.map {
      case id if id > 0 => id -> Success(Tweet(s"Tweet$id", id * 10))
      case id => id -> Failure(new IllegalStateException)
    }.toMap)
  }
}

class UserRepository {
  def usersFor(ids: Set[Long]): Future[Set[User]] = {
    Future.successful(ids.map(id => User(id, s"User$id")))
  }
}

trait ParameterizedUserRepository {
  def usersFor(prefix: String, ids: Set[Long]): Future[Set[User]]
}

class ZipUserRepository {
  def usersFor(ids: List[Long]): Future[List[User]] = {
    Future.successful(ids.map(id => User(id, s"User$id")))
  }
}

class FilteredUserRepository {
  def usersFor(ids: Set[Long]): Future[Set[User]] = {
    Future.successful(ids.filter(_ % 20 == 0).map(id => User(id, s"User$id")))
  }
}

class TimelineRepository {
  def timelinesFor(ids: Set[Int]): Future[Set[Timeline]] = {
    Future.successful(ids.map(id => Timeline(id, List(id * 10, id * 20))))
  }
}

class LikeRepository {
  def likesFor(ids: Set[Long]): Future[Set[Like]] = {
    Future.successful(ids.map(id => Like(id, List(id * 10, id * 20), List(id * 100, id * 200))))
  }
}

class TrackRepository {
  def tracksFor(ids: Set[Long]): Future[Set[Track]] = {
    Future.successful(ids.map(id => Track(id, s"Track$id", id * 10)))
  }
}

class TopTracksRepository {
  def topTracksFor(user: User): Future[Set[Track]] = {
    def track(id: Long): Track = Track(id, s"Track$id", id*100)
    val userId = user.userId
    Future.successful(Set(track(userId), track(userId + 1), track(userId + 2)))
  }
}

class TimelineService {
  def postsFor(id: Long): Future[List[Post]] = Future.successful(List(
    TrackPost(1L, Instant.now()),
    PlaylistPost(2L, Instant.now())
  ))
}
sealed trait Post
case class TrackPost(trackId: Long, timestamp: Instant) extends Post
case class PlaylistPost(playlistId: Long, timestamp: Instant) extends Post

class PlaylistsService {
  def playlistsFor(playlistIds: Set[Long]): Future[Set[Playlist]] = Future.successful(playlistIds.map { id => Playlist(id, s"playlist-$id", playlistIds.map { _ + 1}.toList, id * 100)})
}
case class Playlist(playlistId: Long, name: String, trackIds: List[Long], curatorId: Long)

class PlayStatsService {
  def forTracks(trackIds: Set[Long]): Future[Set[TrackPlayStats]] = Future.successful(trackIds.map { id => TrackPlayStats(id, (id*6).toInt)})
}
case class TrackPlayStats(trackId: Long, playCount: Int)

class LikesService {
  def forEntities(ids: Set[Long]): Future[Set[LikedEntity]] = Future.successful(ids.map { id => LikedEntity(id, (id*7).toInt)})
}
case class LikedEntity(entityId: Long, likeCount: Int) // TODO this would be a list of user ids

class CommentsService {
  def forTracks(ids: Set[Long]): Future[Set[TrackComments]] = Future.successful(ids.map { id => TrackComments(id, List(Comment(s"comment-$id", id * 8, Instant.now()))) })
}

case class TrackComments(trackId: Long, comments: List[Comment])
case class Comment(text: String, commenterId: Long, timestamp: Instant)