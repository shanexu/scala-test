package org.xusheng.scalatest

import cats.effect._
import cats.syntax.all._
import fetch._
import org.xusheng.scalatest.UserDatasource._

/**
 * @author ${user.name}
 */
object App {
  
  def foo(x : Array[String]) = x.foldLeft("")((a,b) => a + b)
  
  def main(args : Array[String]) {
    println( "Hello World!" )
    println("concat arguments = " + foo(args))

    import java.util.concurrent._

    import scala.concurrent.ExecutionContext
    import scala.concurrent.duration._

    val executor = new ScheduledThreadPoolExecutor(4)
    val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)

    implicit val timer: Timer[IO] = IO.timer(executionContext)
    implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

//    println(Fetch.run[IO](fetchUser).unsafeRunTimed(5.seconds))
//
//    println(Fetch.run[IO](fetchTwoUsers).unsafeRunTimed(5.seconds))
//
//    println(Fetch.run[IO](fetchUsers).unsafeRunTimed(5.seconds))
//
//    println(Fetch.run[IO](fetchTwoUsersTupled).unsafeRunTimed(5.seconds))
//
//    println(Fetch.run[IO](fetchCached).unsafeRunTimed(5.seconds))

    println(Fetch.run[IO](fetchListUser(List(1,2,3))).unsafeRunTimed(100.seconds))

    println(Fetch.run[IO](fetchGraph2).unsafeRunTimed(100.seconds))

    executor.shutdown()
  }

  def fetchUser[F[_] : ConcurrentEffect]: Fetch[F, User] = getUser(1)

  def fetchTwoUsers[F[_]: ConcurrentEffect]: Fetch[F, (User, User)] =
    for {
      u1 <- getUser(1)
      u2 <- getUser(u1.id + 1)
    } yield (u1, u2)

  def fetchUsers[F[_]: ConcurrentEffect]: Fetch[F, List[User]] =
    getUsers(List(1, 2, 3))

  def fetchTwoUsersTupled[F[_]: ConcurrentEffect]: Fetch[F, (User, User)] =
    (getUser(1), getUser(2)).tupled

  def fetchCached[F[_] : ConcurrentEffect]: Fetch[F, (User, User)] = for {
    aUser <- getUser(1)
    anotherUser <- getUser(1)
  } yield (aUser, anotherUser)

  def fetchGraph[F[_]: ConcurrentEffect]: Fetch[F, List[User]] = {
    val a = new Node(1)
    val b = new Node(2)
    val c = new Node(3)
    val d = new Node(4)
    val e = new Node(5)
    a.addEdge(b)
    a.addEdge(d)
    b.addEdge(c)
    b.addEdge(e)
    c.addEdge(d)
    c.addEdge(e)
    getGraph(a)
  }

  def fetchGraph2[F[_]: ConcurrentEffect]: Fetch[F, List[User]] = {
    val root = new Node(-1)
    val zero = new Node(0)
    val one = new Node(1)
    val two = new Node(2)
    val three = new Node(3)
    val four = new Node(4)
    val five = new Node(5)

    two.addEdge(three)
    three.addEdge(one)
    four.addEdge(zero)
    four.addEdge(one)
    five.addEdge(zero)
    five.addEdge(two)

    root.addEdge(zero)
    root.addEdge(one)
    root.addEdge(two)
    root.addEdge(three)
    root.addEdge(four)
    root.addEdge(five)

    getGraph(root)
  }

  import cats.instances.list._

  def fetchListUser[F[_]:ConcurrentEffect](ids: List[UserId]): Fetch[F, List[User]] =
    ids.foldM(List.empty[User]){(z, id) => getUser(id).map{u => z :+ u} }

}
