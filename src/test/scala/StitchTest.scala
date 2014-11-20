import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class StitchTest extends FlatSpec with Matchers {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository
  val timelineRepository = new TimelineRepository
  val likeRepository = new LikeRepository
  val trackRepository = new TrackRepository

  case object GetTweetBatchGroup extends SeqGroup[Long, Tweet] {
    def run(calls: Seq[Long]) = tweetRepository.tweetsFor(calls)
  }

  case object GetUserBatchGroup extends SeqGroup[Long, User] {
    def run(calls: Seq[Long]) = userRepository.usersFor(calls)
  }

  case object GetTimelineBatchGroup extends SeqGroup[Long, Timeline] {
    def run(calls: Seq[Long]) = timelineRepository.timelinesFor(calls)
  }

  case object GetLikeBatchGroup extends SeqGroup[Long, Like] {
    def run(calls: Seq[Long]) = likeRepository.likesFor(calls)
  }

  case object GetTrackBatchGroup extends SeqGroup[Long, Track] {
    def run(calls: Seq[Long]) = trackRepository.tracksFor(calls)
  }

  def getTweet(tweetId: Long): Stitch[Tweet] = Stitch.call(tweetId, GetTweetBatchGroup)

  def getUser(userId: Long): Stitch[User] = Stitch.call(userId, GetUserBatchGroup)

  def getTimeline(timelineId: Long): Stitch[Timeline] = Stitch.call(timelineId, GetTimelineBatchGroup)

  def getLike(likeId: Long): Stitch[Like] = Stitch.call(likeId, GetLikeBatchGroup)

  def getTracks(trackIds: Seq[Long]): Stitch[Seq[Track]] = Stitch.collect(trackIds.map(Stitch.call(_, GetTrackBatchGroup)))

  def getUsers(userIds: Seq[Long]): Stitch[Seq[User]] = Stitch.collect(userIds.map(Stitch.call(_, GetUserBatchGroup)))

  "A Stitch" should "batch calls to services" in {
    val tweetIds: Seq[Long] = Seq(1L, 2L, 3L)
    val enrichedTweets: Future[Seq[(Tweet, User)]] = Stitch.traverse(tweetIds) { tweetId =>
      for {
        tweet <- getTweet(tweetId)
        user <- getUser(tweet.userId)
      } yield (tweet, user)
    }.run()

    Await.result(enrichedTweets) === Seq(
      (Tweet("Tweet1", 10), User("User10")),
      (Tweet("Tweet2", 20), User("User20")),
      (Tweet("Tweet3", 30), User("User30")))
  }

  it should "be able to be used in complex nested fetches" in {
    val timelineIds = Seq(1, 3)
    val enrichedTimelines: Future[Seq[(Timeline, Seq[(Like, Seq[Track], Seq[User])])]] =
      Stitch.traverse(timelineIds) { id =>
        for {
          timeline <- getTimeline(id)
          enrichedLikes <- Stitch.traverse(timeline.likeIds) { id =>
            for {
              like <- getLike(id)
              tracks <- getTracks(like.trackIds)
              users <- getUsers(like.userIds)
            } yield (like, tracks, users)
          }
        } yield (timeline, enrichedLikes)
      }.run()

    Await.result(enrichedTimelines) === Seq(
      (Timeline(List(10, 20)), Seq(
        (Like(List(100, 200), List(1000, 2000)), Seq(Track("Track100"), Track("Track200")), Seq(User("User1000"), User("User2000"))),
        (Like(List(200, 400), List(2000, 4000)), Seq(Track("Track200"), Track("Track400")), Seq(User("User2000"), User("User4000")))
      )),
      (Timeline(List(30, 60)), Seq(
        (Like(List(300, 600), List(3000, 6000)), Seq(Track("Track300"), Track("Track600")), Seq(User("User3000"), User("User6000"))),
        (Like(List(600, 1200), List(6000, 12000)), Seq(Track("Track600"), Track("Track1200")), Seq(User("User6000"), User("User12000")))
      ))
    )
  }
}

case class Tweet(body: String, userId: Long)

case class User(name: String)

case class Timeline(likeIds: List[Long])

case class Like(trackIds: List[Long], userIds: List[Long])

case class Track(name: String)

class TweetRepository {
  def tweetsFor(ids: Seq[Long]): Future[Seq[Tweet]] = Future.value(ids.map(id => Tweet(s"Tweet$id", id * 10)))
}

class UserRepository {
  def usersFor(ids: Seq[Long]): Future[Seq[User]] = Future.value(ids.map(id => User(s"User$id")))
}

class TimelineRepository {
  def timelinesFor(ids: Seq[Long]): Future[Seq[Timeline]] = Future.value(ids.map(id => Timeline(List(id * 10, id * 20))))
}

class LikeRepository {
  def likesFor(ids: Seq[Long]): Future[Seq[Like]] = Future.value(ids.map(id => Like(
    List(id * 100, id * 200), List(id * 1000, id * 2000)
  )))
}

class TrackRepository {
  def tracksFor(ids: Seq[Long]): Future[Seq[Track]] = Future.value(ids.map(id => Track(s"Track$id")))
}