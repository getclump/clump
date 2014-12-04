package lilo

import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class LiloSpec extends FlatSpec with Matchers {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository
  val timelineRepository = new TimelineRepository
  val likeRepository = new LikeRepository
  val trackRepository = new TrackRepository

  val tweets = Lilo.source(tweetRepository.tweetsFor) { _.tweetId }
  val users = Lilo.source(userRepository.usersFor) {_.userId }
  val timelines = Lilo.source(timelineRepository.timelinesFor) { _.timelineId }
  val likes = Lilo.source(likeRepository.likesFor) { _.likeId }
  val tracks = Lilo.source(trackRepository.tracksFor) {_.trackId }

  "A Lilo" should "batch calls to services" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Lilo[List[(Tweet, User)]] =
      Lilo.traverse(tweetIds) { tweetId =>
        for {
          tweet <- tweets.get(tweetId)
          user <- users.get(tweet.userId)
        } yield (tweet, user)
      }

    Await.result(enrichedTweets.run) shouldBe Some(Seq(
      (Tweet(1, "Tweet1", 10), User(10, "User10")),
      (Tweet(2, "Tweet2", 20), User(20, "User20")),
      (Tweet(3, "Tweet3", 30), User(30, "User30"))))
  }

  it should "be able to be used in complex nested fetches" in {
    val timelineIds = List(1, 3)
    val enrichedTimelines: Lilo[List[(Timeline, Seq[(Like, List[Track], List[User])])]] =
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

    Await.result(enrichedTimelines.run) shouldBe Some(Seq(
      (Timeline(1, List(10, 20)), List(
        (Like(10, List(100, 200), List(1000, 2000)), List(Track(100, "Track100"), Track(200, "Track200")), List(User(1000, "User1000"), User(2000, "User2000"))),
        (Like(20, List(200, 400), List(2000, 4000)), List(Track(200,"Track200"), Track(400,"Track400")), List(User(2000, "User2000"), User(4000,"User4000"))))),
      (Timeline(3, List(30, 60)), List(
        (Like(30, List(300, 600), List(3000, 6000)), List(Track(300, "Track300"), Track(600, "Track600")), List(User(3000, "User3000"), User(6000, "User6000"))),
        (Like(60, List(600, 1200), List(6000, 12000)), List(Track(600, "Track600"), Track(1200, "Track1200")), List(User(6000, "User6000"), User(12000, "User12000")))))))
  }

  it should "be usable with regular maps and flatMaps" in {
    val tweetIds = List(1L, 2L, 3L)
    val enrichedTweets: Lilo[List[(Tweet, User)]] =
      Lilo.traverse(tweetIds) { tweetId =>
        tweets.get(tweetId).flatMap(tweet =>
          users.get(tweet.userId).map(user => (tweet, user)))
      }

    Await.result(enrichedTweets.run) shouldBe Some(Seq(
      (Tweet(1, "Tweet1", 10), User(10, "User10")),
      (Tweet(2, "Tweet2", 20), User(20, "User20")),
      (Tweet(3, "Tweet3", 30), User(30, "User30"))))
  }
}

case class Tweet(tweetId: Long, body: String, userId: Long)

case class User(userId: Long, name: String)

case class Timeline(timelineId: Long, likeIds: List[Long])

case class Like(likeId: Long, trackIds: List[Long], userIds: List[Long])

case class Track(trackId: Long, name: String)

class TweetRepository {
  def tweetsFor(ids: List[Long]): Future[List[Tweet]] = {
    println("tweets", ids)
    Future.value(ids.map(id => Tweet(id, s"Tweet$id", id * 10)))
  }
}

class UserRepository {
  def usersFor(ids: List[Long]): Future[List[User]] = {
    println("users", ids)
    Future.value(ids.map(id => User(id, s"User$id")))
  }
}

class TimelineRepository {
  def timelinesFor(ids: List[Long]): Future[List[Timeline]] = {
    println("timelines", ids)
    Future.value(ids.map(id => Timeline(id, List(id * 10, id * 20))))
  }
}

class LikeRepository {
  def likesFor(ids: List[Long]): Future[List[Like]] = {
    println("likes", ids)
    Future.value(ids.map(id => Like(id, List(id * 10, id * 20), List(id * 100, id * 200))))
  }
}

class TrackRepository {
  def tracksFor(ids: List[Long]): Future[List[Track]] = {
    println("tracks", ids)
    Future.value(ids.map(id => Track(id, s"Track$id")))
  }
}