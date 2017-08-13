package de.tilltheis

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import org.scalajs.dom
import org.scalajs.dom.html

class View private(dimensions: Dimensions) extends Actor with ActorLogging {
  import de.tilltheis.View._
  import context.dispatcher

  private val canvas = dom.document.getElementById("gameCanvas").asInstanceOf[html.Canvas]

  private val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private val colors = IndexedSeq("#f00", "#0f0", "#00f")

  private var cachedPlayers: Set[Game.Player] = Set.empty
  private var cachedStatus: Game.Status = Game.Running

  private val sixtyFps = (1.0 / 60.0).seconds


  override def preStart(): Unit = {
    canvas.style.display = "block"
    self ! Tick
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def postStop(): Unit = {
    canvas.style.display = "none"
  }

  override def receive: Receive = {
    case Game.GameState(players, status) =>
      cachedPlayers = players
      cachedStatus = status

    case Tick =>
      dom.window.requestAnimationFrame(_ => draw())
      context.system.scheduler.scheduleOnce(sixtyFps, self, Tick)
  }

  private def draw(): Unit = {
    ctx.clearRect(0, 0, dimensions.width, dimensions.height)

    cachedPlayers.zipWithIndex foreach { case (player, index) =>
      val playerColor = colors(index % colors.size)

      val nameLegendOffset = index * 20
      ctx.fillStyle = playerColor
      ctx.fillRect(20, 20 + nameLegendOffset, 20, 5)
      ctx.strokeStyle = "#000"
      ctx.textBaseline = "middle"
      ctx.textAlign = "left"
      ctx.lineWidth = 1
      ctx.strokeText(player.name, 50, 22.5 + nameLegendOffset)


      val bodyWidth = 5.0d

      player.body.headOption foreach { point =>
        ctx.translate(point.x, point.y)
        ctx.rotate(-player.orientation)

        ctx.beginPath()
        ctx.moveTo(+bodyWidth/2, 0)
        ctx.lineTo(-bodyWidth/2, +bodyWidth/2)
        ctx.lineTo(-bodyWidth/2, -bodyWidth/2)

        ctx.fill()

        ctx.rotate(player.orientation)
        ctx.translate(-point.x, -point.y)
      }

      ctx.beginPath()

      (player.body zip player.body.tail) foreach { case (lastPoint, currentPoint) =>
        if (Math.abs(lastPoint.x - currentPoint.x) >= dimensions.width / 2 ||
          Math.abs(lastPoint.y - currentPoint.y) >= dimensions.height / 2) {
          ctx.moveTo(currentPoint.x, currentPoint.y)
        }
        ctx.lineTo(currentPoint.x, currentPoint.y)
      }

      ctx.strokeStyle = playerColor
      ctx.lineWidth = bodyWidth

      ctx.stroke()
    }

    cachedStatus match {
      case Game.Finished(winner +: _) =>
        ctx.strokeStyle = "#000"
        ctx.lineWidth = 1
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.strokeText(s"$winner WON!", dimensions.width / 2, dimensions.height / 2)
      case _ => ()
    }
  }
}

object View {
  def props(dimensions: Dimensions): Props = Props(new View(dimensions))

  private case object Tick
  case class Player(name: String, body: Seq[Point])

}