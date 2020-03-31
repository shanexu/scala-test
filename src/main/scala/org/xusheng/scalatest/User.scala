package org.xusheng.scalatest

import scala.collection.mutable.ListBuffer

object UserDatasource {
  type UserId = Int

  case class User(id: UserId, username: String)

  class Node(val name: UserId) {
    val edges: ListBuffer[Node] = ListBuffer()
    def addEdge(node: Node) = {
      edges.append(node)
    }
  }

  import cats.effect._
  import cats.syntax.all._

  def latency[F[_] : Concurrent](msg: String): F[Unit] = for {
    _ <- Sync[F].delay(println(s"--> [${Thread.currentThread.getId}] $msg"))
    _ <- Sync[F].delay(Thread.sleep(5000))
    _ <- Sync[F].delay(println(s"<-- [${Thread.currentThread.getId}] $msg"))
  } yield ()

  import cats.data.NonEmptyList
  import fetch._

  val userDatabase: Map[UserId, User] = Map(
    -1 -> User(-1, "@root"),
    0 -> User(0, "@zero"),
    1 -> User(1, "@one"),
    2 -> User(2, "@two"),
    3 -> User(3, "@three"),
    4 -> User(4, "@four"),
    5 -> User(5, "@five"),
    6 -> User(6, "@six"),
    7 -> User(7, "@seven"),
  )

  object Users extends Data[UserId, User] {
    def name = "Users"

    def source[F[_] : ConcurrentEffect]: DataSource[F, UserId, User] = new DataSource[F, UserId, User] {
      override def data = Users

      override def CF = ConcurrentEffect[F]

      override def fetch(id: UserId): F[Option[User]] =
        latency[F](s"One User $id") >> CF.pure(userDatabase.get(id))

      override def maxBatchSize: Option[Int] = Some(2)

      override def batchExecution: BatchExecution = InParallel

      override def batch(ids: NonEmptyList[UserId]): F[Map[UserId, User]] =
        latency[F](s"Batch Users $ids") >> CF.pure(userDatabase.filterKeys(ids.toList.toSet).toMap)
    }
  }

  def getUser[F[_] : ConcurrentEffect](id: UserId): Fetch[F, User] =
    Fetch(id, Users.source)

  def getUserName[F[_] : ConcurrentEffect](id: UserId): Fetch[F, String] =
    Fetch(id, Users.source).map{u => u.username}

  def maybeGetUser[F[_] : ConcurrentEffect](id: UserId): Fetch[F, Option[User]] =
    Fetch.optional(id, Users.source)

  import cats.instances.list._

  def getUsers[F[_]: ConcurrentEffect](ids: List[UserId]): Fetch[F, List[User]] =
    ids.traverse(i => getUser(i))

  def getGraph[F[_] : ConcurrentEffect](graph: Node): Fetch[F, List[User]] = {
    for {
      us <- graph.edges.toList.traverse(node => getGraph(node))
      u <- getUser(graph.name)
    } yield us.flatten :+ u
  }

}

