package de.tilltheis

import scala.concurrent.duration._
import akka.actor.{ActorRef, FSM, Props}
import de.tilltheis.Server._

object Server {
  def props: Props = Props(new Server)

  // commands
  case class JoinPlayer(player: String)
  case object StartGame

  // events
  case class PlayerJoined(name: String)
  case object GameStarted

  // internal
  sealed trait ServerState
  case object AwaitingPlayers extends ServerState
  case object RunningSimulation extends ServerState
  case object GameEnded extends ServerState

  sealed trait ServerData
  case class JoinedPlayers(names: Set[String]) extends ServerData
  case class Simulation(players: Set[String], game: ActorRef) extends ServerData
}

class Server private () extends FSM[ServerState, ServerData] {
  startWith(AwaitingPlayers, JoinedPlayers(Set.empty))

  when(AwaitingPlayers) {
    case Event(JoinPlayer(name), JoinedPlayers(existingPlayers)) =>
      context.parent ! PlayerJoined(name)
      stay using JoinedPlayers(existingPlayers + name)

    case Event(StartGame, JoinedPlayers(players)) =>
      startGame(players)
  }

  private def startGame(players: Set[String]) = {
    val game = context.actorOf(Game.props(Dimensions(500, 500), players))
    context.parent ! GameStarted
    goto(RunningSimulation) using Simulation(players, game)
  }

  onTransition {
    case _ -> RunningSimulation => nextStateData match {
      case simulation: Simulation => simulation.game ! Game.StartPlaying
      case _ =>
    }
  }

  when(RunningSimulation) {
    case Event(Game.SteerPlayer(player, direction), simulation: Simulation) =>
      simulation.game ! Game.SteerPlayer(player, direction)
      stay

    case Event(gameState: Game.GameState, simulation: Simulation) =>
      context.parent ! Game.GameStateChanged(gameState)

      gameState.state match {
        case Game.Running => stay()
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
