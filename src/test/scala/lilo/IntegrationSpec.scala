package lilo

import com.twitter.util.{ Await, Future }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Spec {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository
  val timelineRepository = new TimelineRepository
  val likeRepository = new LikeRepository
  val trackRepository = new TrackRepository

  val tweets = Lilo.source(tweetRepository.tweetsFor)
  val users = Lilo.source(userRepository.usersFor)
  val timelines = Lilo.source(timelineRepository.timelinesFor)
  val likes = Lilo.source(likeRepository.likesFor)
  val tracks = Lilo.source(trackRepository.tracksFor)

  "A Lilo should batch calls to services" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Lilo[List[(Tweet, User)]] =
      Lilo.collect {
        tweetIds.map { tweetId =>
          for {
            tweet <- tweets.get(tweetId)
            user <- users.get(tweet.userId)
          } yield (tweet, user)
        }
      }

    Await.result(enrichedTweets.run) ==== Some(List(
      (Tweet("Tweet1", 10), User("User10")),
      (Tweet("Tweet2", 20), User("User20")),
      (Tweet("Tweet3", 30), User("User30"))))
  }

  "it should be able to be used in complex nested fetches" in {
    val timelineIds = List(1, 3)
    val enrichedTimelines = //: Lilo[Seq[(Timeline, Seq[(Like, Seq[Track], Seq[User])])]] =
      Lilo.traverse(timelineIds) { id =>
        for {
          timeline <- timelines.get(id)
          enrichedLikes <- Lilo.traverse(timeline.likeIds) { id =>
            for {
              like <- likes.get(id)
              resources <- tracks.get(like.trackIds).join(users.get(like.userIds))
            } yield (like, resources._1, resources._2)
          }
        } yield (timeline, enrichedLikes)
      }

    Await.result(enrichedTimelines.run) ==== Some(List(
      (Timeline(List(10, 20)), List(
        (Like(List(100, 200), List(1000, 2000)), List(Track("Track100"), Track("Track200")), List(User("User1000"), User("User2000"))),
        (Like(List(200, 400), List(2000, 4000)), List(Track("Track200"), Track("Track400")), List(User("User2000"), User("User4000"))))),
      (Timeline(List(30, 60)), List(
        (Like(List(300, 600), List(3000, 6000)), List(Track("Track300"), Track("Track600")), List(User("User3000"), User("User6000"))),
        (Like(List(600, 1200), List(6000, 12000)), List(Track("Track600"), Track("Track1200")), List(User("User6000"), User("User12000")))))))
  }

  "it should be usable with regular maps and flatMaps" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Lilo[List[(Tweet, User)]] =
      Lilo.traverse(tweetIds) { tweetId =>
        tweets.get(tweetId).flatMap(tweet =>
          users.get(tweet.userId).map(user => (tweet, user)))
      }

    Await.result(enrichedTweets.run) ==== Some(List(
      (Tweet("Tweet1", 10), User("User10")),
      (Tweet("Tweet2", 20), User("User20")),
      (Tweet("Tweet3", 30), User("User30"))))
  }
}

case class Tweet(body: String, userId: Long)

case class User(name: String)

case class Timeline(likeIds: List[Long])

case class Like(trackIds: List[Long], userIds: List[Long])

case class Track(name: String)

class TweetRepository {
  def tweetsFor(ids: Seq[Long]): Future[Map[Long, Tweet]] = {
    println("tweets", ids)
    Future.value(ids.map(id => id -> Tweet(s"Tweet$id", id * 10)).toMap)
  }
}

class UserRepository {
  def usersFor(ids: Seq[Long]): Future[Map[Long, User]] = {
    println("users", ids)
    Future.value(ids.map(id => id -> User(s"User$id")).toMap)
  }
}

class TimelineRepository {
  def timelinesFor(ids: Seq[Long]): Future[Map[Long, Timeline]] = {
    println("timelines", ids)
    Future.value(ids.map(id => id -> Timeline(List(id * 10, id * 20))).toMap)
  }
}

class LikeRepository {
  def likesFor(ids: Seq[Long]): Future[Map[Long, Like]] = {
    println("likes", ids)
    Future.value(ids.map(id => id -> Like(
      List(id * 10, id * 20), List(id * 100, id * 200))).toMap)
  }
}

class TrackRepository {
  def tracksFor(ids: Seq[Long]): Future[Map[Long, Track]] = {
    println("tracks", ids)
    Future.value(ids.map(id => id -> Track(s"Track$id")).toMap)
  }
}