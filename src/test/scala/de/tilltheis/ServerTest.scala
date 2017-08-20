package de.tilltheis

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActors, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class ServerTest extends MyTestKit(ActorSystem()) with WordSpecLike with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Server actor" when {
    "awaiting players" when {
      "receiving JoinPlayer commands" should {
        "dispatch PlayerJoined events" in {
          val testProbe = MyTestProbe()
          val server = testProbe.childActorOf(Server.props(Function.const(Props.empty)))

          server ! Server.JoinPlayer("player1")
          server ! Server.JoinPlayer("player2")

          testProbe.expectMsg(Server.PlayerJoined("player1"))
          testProbe.expectMsg(Server.PlayerJoined("player2"))
        }
      }

      "receiving a StartGame command" should {
        "start the game with all players that have joined until then and command it to start playing" in {
          val testProbe = MyTestProbe()
          val gameProbe = MyTestProbe()

          def gameProps(players: Set[String]): Props = {
            testProbe.ref ! players
            TestActors.forwardActorProps(gameProbe.ref)
          }

          val server = system.actorOf(Server.props(gameProps))

          server ! Server.JoinPlayer("player1")
          server ! Server.JoinPlayer("player2")
          server ! Server.StartGame

          testProbe.expectMsg(Set("player1", "player2"))
          gameProbe.expectMsg(Game.StartPlaying)
          testProbe.expectNoMsg
        }
      }
    }

    "running simulation" when {
      "receiving JoinPlayer commands" should {
        "do nothing" in {
          val testProbe = MyTestProbe()
          val server = testProbe.childActorOf(Server.props(Function.const(Props.empty)))

          server ! Server.StartGame
          server ! Server.JoinPlayer("player1")

          testProbe.expectNoMsg
        }
      }

      "receiving SteerPlayer commands" should {
        "forward them to the game" in {
          val gameProbe = MyTestProbe()
          val gameProps = TestActors.forwardActorProps(gameProbe.ref)
          val server = system.actorOf(Server.props(Function.const(gameProps)))

          server ! Server.StartGame
          server ! Game.SteerPlayer("foo", None)
          server ! Game.SteerPlayer("bar", Some(Direction.Left))
          server ! Game.SteerPlayer("baz", Some(Direction.Right))

          gameProbe.expectMsg(Game.StartPlaying)
          gameProbe.expectMsg(Game.SteerPlayer("foo", None))
          gameProbe.expectMsg(Game.SteerPlayer("bar", Some(Direction.Left)))
          gameProbe.expectMsg(Game.SteerPlayer("baz", Some(Direction.Right)))
        }
      }
    }
  }
}
