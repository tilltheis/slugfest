package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.tilltheis.User.KeyCode
import org.scalajs.dom
import org.scalajs.dom.ext
import org.scalajs.dom.raw.KeyboardEvent

class User private(name: String, leftKey: KeyCode, rightKey: KeyCode, game: ActorRef) extends Actor with ActorLogging {
  import de.tilltheis.User._

  private var pressedKey: Option[KeyCode] = None

  private def registerListener(eventName: String, createEvent: Int => KeyEvent): Unit = {
    dom.document.addEventListener[KeyboardEvent](eventName, { event =>
      if (!event.getModifierState("Accel") && !event.repeat && Set(leftKey, rightKey).contains(event.keyCode)) {
        event.preventDefault()
        self ! createEvent(event.keyCode)
      }
    })
  }

  override def preStart(): Unit = {
    registerListener("keydown", KeyDown)
    registerListener("keyup", KeyUp)
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def receive: Receive = {
    case KeyDown(keyCode) =>
      pressedKey = Some(keyCode)
      val direction = if (keyCode == leftKey) Direction.Left else Direction.Right
      game ! Game.SteerPlayer(name, Some(direction))

    case KeyUp(keyCode) =>
      // support key overlay by only acknowledging the last hit key
      if (pressedKey contains keyCode) {
        pressedKey = None
        game ! Game.SteerPlayer(name, None)
      }
  }
}

object User {
  def props(name: String, leftKey: KeyCode, rightKey: KeyCode, game: ActorRef): Props =
    Props(new User(name, leftKey, rightKey, game))

  type KeyCode = Int
  val KeyCode: ext.KeyCode.type = ext.KeyCode

  private sealed trait KeyEvent
  private case class KeyDown(keyCode: KeyCode) extends KeyEvent
  private case class KeyUp(keyCode: KeyCode) extends KeyEvent
}