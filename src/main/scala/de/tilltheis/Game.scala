package de.tilltheis

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props}

import scala.util.Random

object Game {
  def props(dimensions: Dimensions, players: Set[String]): Props = Props(new Game(dimensions, players))

  // commands
  case object StartPlaying
  case class SteerPlayer(player: String, steeringDirection: Option[Direction])

  // events
  case class GameStateChanged(gameState: GameState)

  // helpers
  case class Player(name: String, body: List[Point], orientation: Double, steeringDirection: Option[Direction], hasCollided: Boolean)
  sealed trait Status
  case object Running extends Status
  case class Finished(winningOrder: Seq[String]) extends Status
  case class GameState(players: Set[Player], state: Status)

  // internal
  private case object Tick
}

class Game private(dimensions: Dimensions, playerNames: Set[String]) extends Actor with ActorLogging {
  import de.tilltheis.Game._
  import context.dispatcher

  require(dimensions.width > 0)
  require(dimensions.height > 0)

  private val hundredFps = (1.0 / 100.0).seconds

  private def randomPoint = Point(Random.nextInt(dimensions.width), Random.nextInt(dimensions.height))
  private var directions: Map[String, Option[Direction]] = (playerNames map (_ -> None)).toMap

  private var players: Map[String, Player] = (playerNames map { name =>
    name -> Player(name, List(randomPoint), Math.toRadians(Random.nextInt(360)), None, hasCollided = false)
  }).toMap

  private var losingOrder = Seq.empty[String]

  override def receive: Receive = {
    case StartPlaying =>
      // notify about initial state
      notifyServerAndScheduleTick()

    case Tick =>
      runFrame()
      notifyServerAndScheduleTick()

    case SteerPlayer(player, maybeDirection) =>
      directions = directions.updated(player, maybeDirection)
  }

  private def notifyServerAndScheduleTick() = {
    val status = if (losingOrder.size < players.size - 1) Running else Finished((playerNames filterNot losingOrder.contains).toSeq ++ losingOrder.reverse)
    context.parent ! GameState(players.values.toSet, status)
    if (status == Running) {
      context.system.scheduler.scheduleOnce(hundredFps, self, Tick)
    }
  }

  private def runFrame(): Unit = {
    val positioned = directions.foldLeft(players) {
      case (map, (playerName, _)) if map(playerName).hasCollided => map
      case (map, (playerName, maybeDirection)) =>
        val player = map(playerName)
        val orientationFactor = maybeDirection.fold(0) {
          case Direction.Left => +1
          case Direction.Right => -1
        }
        val newOrientation = ((player.orientation + 0.08 * orientationFactor) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
        val newHead = player.body.head.copy(
          x = player.body.head.x + 1.5 * Math.cos(newOrientation),
          y = player.body.head.y - 1.5 * Math.sin(newOrientation))
        val newBody = newHead.clipTo(dimensions) :: player.body
        map.updated(playerName, player.copy(body = newBody, orientation = newOrientation))
    }

    val collided = positioned map {
      case (name, player) if player.hasCollided => name -> player
      case (name, player) =>
        val hasCollided = positioned.values exists { otherPlayer =>
          val body = if (otherPlayer.name == player.name) otherPlayer.body.tail else otherPlayer.body
          body exists (isNearPoint(_, player.body.head))
        }
        name -> player.copy(hasCollided = hasCollided)
    }

    players = collided

    losingOrder ++= (collided.values collect {
      case player if player.hasCollided && !(losingOrder contains player.name) => player.name
    })
  }

  private def isNearPoint(point1: Point, point2: Point) = {
    val notNearDistance = 1.0d
    Math.abs(point1.x - point2.x) < notNearDistance && Math.abs(point1.y - point2.y) < notNearDistance
  }
}
