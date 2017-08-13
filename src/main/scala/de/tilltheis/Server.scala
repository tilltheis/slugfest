package de.tilltheis

import scala.concurrent.duration._
import akka.actor.{ActorRef, FSM, Props}
import de.tilltheis.Server._

object Server {
  def props: Props = Props(new Server)

  sealed trait ServerState
  case object AwaitingClients extends ServerState
  case object RunningSimulation extends ServerState
  case object GameEnded extends ServerState

  sealed trait ServerData
  case class Clients(clients: Map[ActorRef, Set[String]]) extends ServerData
  case class Simulation(clients: Map[ActorRef, Set[String]], game: ActorRef) extends ServerData

  // incoming messages
  case class Join(client: ActorRef, names: Set[String])
  case object StartGame

  // outgoing messages
  case class UserJoined(name: String)
}

class Server private () extends FSM[ServerState, ServerData] {
  startWith(AwaitingClients, Clients(Map.empty))

  when(AwaitingClients) {
    case Event(Join(newClientActor, newUserNames), Clients(existingClients)) =>
      newUserNames foreach { name =>
        context.parent ! UserJoined(name)
        existingClients.keySet foreach (_ ! UserJoined(name))
      }
      stay using Clients(existingClients + (newClientActor -> newUserNames))

    case Event(StartGame, Clients(clients)) =>
      startGame(clients)
  }

  private def startGame(clients: Map[ActorRef, Set[String]]) = {
    val game = context.actorOf(Game.props(Dimensions(500, 500), clients.values.flatten.toSet, self))
    clients.keySet foreach (_ ! StartGame)
    goto(RunningSimulation) using Simulation(clients, game)
  }

  onTransition {
    case _ -> RunningSimulation => nextStateData match {
      case simulation: Simulation => simulation.game ! Game.StartPlaying
      case _ =>
    }
  }

  when(RunningSimulation) {
    case Event(Game.PlayerAction(userName, direction), simulation: Simulation) =>
      simulation.game ! Game.PlayerAction(userName, direction)
      stay

    case Event(gameState: Game.GameState, simulation: Simulation) =>
      simulation.clients.keys foreach (_ ! gameState)

      gameState.state match {
        case Game.Running => stay()
        case _: Game.Finished =>
          goto(GameEnded) using Clients(simulation.clients)
      }
  }

  when(GameEnded, 1.seconds) {
    case Event(StateTimeout, Clients(clients)) =>
      startGame(clients)
  }

  initialize()
}
