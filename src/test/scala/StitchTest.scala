import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class StitchTest extends FlatSpec with Matchers {
  val tweetRepository = new TweetRepository
  val userRepository = new UserRepository

  case object GetTweetBatchGroup extends SeqGroup[Long, Tweet] {
    def run(calls: Seq[Long]) = tweetRepository.tweetsFor(calls)
  }

  case object GetUserBatchGroup extends SeqGroup[Long, User] {
    def run(calls: Seq[Long]) = userRepository.usersFor(calls)
  }

  def getTweet(tweetId: Long): Stitch[Tweet] = Stitch.call(tweetId, GetTweetBatchGroup)

  def getUser(userId: Long): Stitch[User] = Stitch.call(userId, GetUserBatchGroup)

  "A Stitch" should "batch calls to services" in {
    val enrichedTweets: Future[Seq[(Tweet, User)]] = Stitch.traverse(Seq(1L, 2L, 3L)) { tweetId =>
      for {
        tweet <- getTweet(tweetId)
        user <- getUser(tweet.userId)
      } yield (tweet, user)
    }.run()
    Await.result(enrichedTweets) === Seq(
      (Tweet("tweet1", 10), User("User10")),
      (Tweet("tweet2", 20), User("User20")),
      (Tweet("tweet3", 30), User("User30")))
  }
}

case class Tweet(body: String, userId: Long)

case class User(name: String)

class TweetRepository {
  def tweetsFor(ids: Seq[Long]): Future[Seq[Tweet]] = Future.value(ids.map(id => Tweet(s"tweet$id", id * 10)))
}

class UserRepository {
  def usersFor(ids: Seq[Long]): Future[Seq[User]] = Future.value(ids.map(id => User(s"User$id")))
}

