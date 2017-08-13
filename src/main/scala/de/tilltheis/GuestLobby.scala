package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, Props}
import de.tilltheis.LocalClient.UserSettings
import org.scalajs.dom
import org.scalajs.dom.html

object GuestLobby {
  def props(serverPeerId: String,
            userName: String,
            serverName: String,
            networkerClientProps: String => Props,
            viewProps: Props,
            localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props): Props =
    Props(new GuestLobby(serverPeerId, userName, serverName, networkerClientProps, viewProps, localClientProps))

  object LobbyType extends Enumeration {
    val Host, Guest = Value
  }
}

class GuestLobby private(serverPeerId: String,
                         userName: String,
                         serverName: String,
                         networkerClientProps: String => Props,
                         viewProps: Props,
                         localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props) extends Actor with ActorLogging {
  private val lobbyWidget = dom.document.getElementById("lobbyWidget").asInstanceOf[html.Element]
  private val userList = dom.document.getElementById("lobbyUserList").asInstanceOf[html.OList]
  private val quitLobbyButton = dom.document.getElementById("quitLobbyButton").asInstanceOf[html.Button]
  private val startGameButton = dom.document.getElementById("startServerGameButton").asInstanceOf[html.Button]

  private val networkerClient = context.actorOf(networkerClientProps(serverPeerId))
  private val userSettings = Set(
    LocalClient.UserSettings(userName, User.KeyCode.Left, User.KeyCode.Right))
  private val localClient = context.actorOf(localClientProps(networkerClient, self, userSettings))
  networkerClient ! Server.Join(localClient, userSettings map (_.name))

  userList.innerHTML = ""
  self ! Server.UserJoined(serverName) // hack until we got proper complete initialization

  override def preStart(): Unit = {
    lobbyWidget.style.display = "flex"
    startGameButton.style.display = "none"
    quitLobbyButton.onclick = _ => self ! Kill
    startGameButton.onclick = Function.const(())
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def postStop(): Unit = {
    lobbyWidget.style.display = "none"
  }


  def receiveInLobby: Receive = {
    case Server.UserJoined(name) =>
      val listItem = dom.document.createElement("li")
      listItem.innerHTML = escapeHtml(name)
      userList.appendChild(listItem)

    case Server.StartGame =>
      lobbyWidget.style.display = "none"
      context.parent ! Server.StartGame
      context.become(receiveInGame(context.actorOf(viewProps)))
  }

  def receiveInGame(view: ActorRef): Receive = {
    case gameState: Game.GameState =>
      view ! gameState
  }


  override def receive: Receive = receiveInLobby

}
