package de.tilltheis

import scala.concurrent.duration._
import akka.actor.{ActorRef, FSM, Props}
import de.tilltheis.Server._

object Server {
  def props(gameProps: Set[String] => Props): Props = Props(new Server(gameProps))

  // commands
  case class JoinPlayer(player: String)
  case object StartGame

  // events
  case class PlayerJoined(name: String)

  // internal
  sealed trait ServerState
  case object AwaitingPlayers extends ServerState
  case object RunningSimulation extends ServerState
  case object GameEnded extends ServerState

  sealed trait ServerData
  case class JoinedPlayers(names: Set[String]) extends ServerData
  case class Simulation(players: Set[String], game: ActorRef) extends ServerData
}

class Server private(gameProps: Set[String] => Props) extends FSM[ServerState, ServerData] {
  startWith(AwaitingPlayers, JoinedPlayers(Set.empty))

  when(AwaitingPlayers) {
    case Event(JoinPlayer(name), JoinedPlayers(existingPlayers)) =>
      context.parent ! PlayerJoined(name)
      stay using JoinedPlayers(existingPlayers + name)

    case Event(StartGame, JoinedPlayers(players)) =>
      startGame(players)
  }

  private def startGame(players: Set[String]) = {
    val game = context.actorOf(gameProps(players))
    goto(RunningSimulation) using Simulation(players, game)
  }

  onTransition {
    case _ -> RunningSimulation => nextStateData match {
      case simulation: Simulation => simulation.game ! Game.StartPlaying
      case _ =>
    }
  }

  when(RunningSimulation) {
    case Event(started@Game.Started, _) =>
      context.parent ! started
      stay

    case Event(Game.SteerPlayer(player, direction), simulation: Simulation) =>
      simulation.game ! Game.SteerPlayer(player, direction)
      stay

    case Event(gameState: Game.GameState, simulation: Simulation) =>
      context.parent ! Game.GameStateChanged(gameState)

      gameState.state match {
        case Game.Started => stay()
        case _: Game.Finished =>
          goto(GameEnded) using JoinedPlayers(simulation.players)
      }
  }

  when(GameEnded, 1.seconds) {
    case Event(StateTimeout, JoinedPlayers(clients)) =>
      startGame(clients)
  }

  initialize()
}
