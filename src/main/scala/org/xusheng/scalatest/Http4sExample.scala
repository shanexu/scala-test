package org.xusheng.scalatest

import java.util.concurrent._

import cats.data.NonEmptyList
import cats.effect._
import cats.instances.list._
import cats.syntax.all._
import fetch._
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze._

import scala.concurrent.ExecutionContext

object HttpExample {
  case class UserId(id: Int)
  case class PostId(id: Int)

  case class User(id: UserId, name: String, username: String, email: String)
  case class Post(id: PostId, userId: UserId, title: String, body: String)

  object Http {
    val executionContext =
      ExecutionContext.fromExecutor(new ScheduledThreadPoolExecutor(2))

    def client[F[_]: ConcurrentEffect]: Resource[F, Client[F]] =
      BlazeClientBuilder[F](executionContext).resource

    implicit val userIdDecoder: Decoder[UserId] = Decoder[Int].map(UserId.apply)
    implicit val postIdDecoder: Decoder[PostId] = Decoder[Int].map(PostId.apply)
    implicit val userDecoder: Decoder[User]     = deriveDecoder
    implicit val postDecoder: Decoder[Post]     = deriveDecoder
  }

  object Users extends Data[UserId, User] {
    import Http._

    def name = "Users"

    def http[F[_]: ConcurrentEffect]: DataSource[F, UserId, User] =
      new DataSource[F, UserId, User] {
        def data = Users

        override def CF = ConcurrentEffect[F]

        override def fetch(id: UserId): F[Option[User]] = {
          val url = s"https://jsonplaceholder.typicode.com/users?id=${id.id}"
          client[F].use((c) => c.expect(url)(jsonOf[F, List[User]])).map(_.headOption)
        }

        override def batch(ids: NonEmptyList[UserId]): F[Map[UserId, User]] = {
          val filterIds = ids.map("id=" + _.id).toList.mkString("&")
          val url       = s"https://jsonplaceholder.typicode.com/users?$filterIds"
          val io        = client[F].use((c) => c.expect(url)(jsonOf[F, List[User]]))
          io.map(users => users.map(user => user.id -> user).toMap)
        }
      }
  }

  object Posts extends Data[UserId, List[Post]] {
    import Http._

    def name = "Posts"

    def http[F[_]: ConcurrentEffect]: DataSource[F, UserId, List[Post]] =
      new DataSource[F, UserId, List[Post]] {
        def data = Posts

        override def CF = ConcurrentEffect[F]

        override def fetch(id: UserId): F[Option[List[Post]]] = {
          val url = s"https://jsonplaceholder.typicode.com/posts?userId=${id.id}"
          client[F].use((c) => c.expect(url)(jsonOf[F, List[Post]])).map(Option.apply)
        }

        override def batch(ids: NonEmptyList[UserId]): F[Map[UserId, List[Post]]] = {
          val filterIds = ids.map("userId=" + _.id).toList.mkString("&")
          val url       = s"https://jsonplaceholder.typicode.com/posts?$filterIds"
          client[F].use((c) => c.expect(url)(jsonOf[F, List[Post]])).map(_.groupBy(_.userId).toMap)
        }
      }
  }

  def fetchUserById[F[_]: ConcurrentEffect](id: UserId): Fetch[F, User] =
    Fetch(id, Users.http)

  def fetchPostsForUser[F[_]: ConcurrentEffect](id: UserId): Fetch[F, List[Post]] =
    Fetch(id, Posts.http)

  def fetchUser[F[_]: ConcurrentEffect](id: Int): Fetch[F, User] =
    fetchUserById(UserId(id))

  def fetchManyUsers[F[_]: ConcurrentEffect](ids: List[Int]): Fetch[F, List[User]] =
    ids.traverse(i => fetchUserById(UserId(i)))

  def fetchPosts[F[_]: ConcurrentEffect](user: User): Fetch[F, (User, List[Post])] =
    fetchPostsForUser(user.id).map(posts => (user, posts))
}
