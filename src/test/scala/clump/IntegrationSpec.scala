package clump

import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Spec {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository
  val zipUserRepository = new ZipUserRepository
  val filteredUserRepository = new FilteredUserRepository
  val timelineRepository = new TimelineRepository
  val likeRepository = new LikeRepository
  val trackRepository = new TrackRepository

  val tweets = ClumpSource.from(tweetRepository.tweetsFor)
  val users = ClumpSource.from(userRepository.usersFor)
  val filteredUsers = ClumpSource(filteredUserRepository.usersFor) { _.userId }
  val zippedUsers = ClumpSource.zip(zipUserRepository.usersFor)
  val timelines = ClumpSource(timelineRepository.timelinesFor) { _.timelineId }
  val likes = ClumpSource(likeRepository.likesFor) { _.likeId }
  val tracks = ClumpSource(trackRepository.tracksFor) { _.trackId }

  "A Clump should batch calls to services" in {
    val enrichedTweets = Clump.traverse(List(1L, 2L, 3L)) { tweetId =>
        for {
          tweet <- tweets.get(tweetId)
          user <- users.get(tweet.userId)
        } yield (tweet, user)
      }

    Await.result(enrichedTweets.get) ==== Some(List(
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
              resources <- tracks.get(like.trackIds).join(users.get(like.userIds))
            } yield (like, resources._1, resources._2)
          }
        } yield (timeline, enrichedLikes)
      }

    Await.result(enrichedTimelines.get) ==== Some(List(
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

    Await.result(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "it should allow unwrapping Clumped lists with clump.list" in {
    val enrichedTweets: Clump[List[(Tweet, User)]] = Clump.traverse(List(1L, 2L, 3L)) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- users.get(tweet.userId)
      } yield (tweet, user)
    }

    Await.result(enrichedTweets.list) ==== List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30")))
  }

  "it should work with Clump.sourceZip" in {
    val enrichedTweets = Clump.traverse(List(1L, 2L, 3L)) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- zippedUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    Await.result(enrichedTweets.get) ==== Some(List(
      (Tweet("Tweet1", 10), User(10, "User10")),
      (Tweet("Tweet2", 20), User(20, "User20")),
      (Tweet("Tweet3", 30), User(30, "User30"))))
  }

  "A Clump can have a partial result" in {
    val onlyFullObjectGraph: Clump[List[(Tweet, User)]] = Clump.traverse(List(1L, 2L, 3L)) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- filteredUsers.get(tweet.userId)
      } yield (tweet, user)
    }

    Await.result(onlyFullObjectGraph.get) ==== Some(List((Tweet("Tweet2", 20), User(20, "User20"))))

    val partialResponses: Clump[List[(Tweet, Option[User])]] = Clump.traverse(List(1L, 2L, 3L)) { tweetId =>
      for {
        tweet <- tweets.get(tweetId)
        user <- filteredUsers.get(tweet.userId).optional
      } yield (tweet, user)
    }

    Await.result(partialResponses.get) ==== Some(List(
      (Tweet("Tweet1", 10), None),
      (Tweet("Tweet2", 20), Some(User(20, "User20"))),
      (Tweet("Tweet3", 30), None)))
  }
}

case class Tweet(body: String, userId: Long)

case class User(userId: Long, name: String)

case class Timeline(timelineId: Int, likeIds: List[Long])

case class Like(likeId: Long, trackIds: List[Long], userIds: List[Long])

case class Track(trackId: Long, name: String)

class TweetRepository {
  def tweetsFor(ids: Set[Long]): Future[Map[Long, Tweet]] = {
    Future.value(ids.map(id => id -> Tweet(s"Tweet$id", id * 10)).toMap)
  }
}

class UserRepository {
  def usersFor(ids: Set[Long]): Future[Map[Long, User]] = {
    Future.value(ids.map(id => id -> User(id, s"User$id")).toMap)
  }
}

class ZipUserRepository {
  def usersFor(ids: List[Long]): Future[List[User]] = {
    Future.value(ids.map(id => User(id, s"User$id")))
  }
}

class FilteredUserRepository {
  def usersFor(ids: Set[Long]): Future[Set[User]] = {
    Future.value(ids.filter(_ % 20 == 0).map(id => User(id, s"User$id")))
  }
}

class TimelineRepository {
  def timelinesFor(ids: Set[Int]): Future[Set[Timeline]] = {
    Future.value(ids.map(id => Timeline(id, List(id * 10, id * 20))))
  }
}

class LikeRepository {
  def likesFor(ids: Set[Long]): Future[Set[Like]] = {
    Future.value(ids.map(id => Like(id, List(id * 10, id * 20), List(id * 100, id * 200))))
  }
}

class TrackRepository {
  def tracksFor(ids: Set[Long]): Future[Set[Track]] = {
    Future.value(ids.map(id => Track(id, s"Track$id")))
  }
}