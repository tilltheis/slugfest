package de.tilltheis

import akka.actor.{Actor, ActorRef, Props}
import de.tilltheis.LocalClient.UserSettings
import de.tilltheis.User.KeyCode
import org.scalajs.dom.html

object LocalClient {
  def props(server: ActorRef, view: ActorRef, userSettings: Set[UserSettings], element: html.Element): Props =
    Props(new LocalClient(server, view, userSettings, element))

  case class UserSettings(name: String, leftKey: KeyCode, rightKey: KeyCode)
}

class LocalClient private (server: ActorRef, view: ActorRef, userSettings: Set[UserSettings], element: html.Element) extends Actor {
  private val users = userSettings map { settings =>
    val user = User.props(settings.name, settings.leftKey, settings.rightKey, self, element)
    context.actorOf(user, s"user_${settings.name}")
  }

  override def receive: Receive = {
    case playerAction: Game.PlayerAction => server ! playerAction
    case gameState: Game.GameState => view ! gameState
  }
}
