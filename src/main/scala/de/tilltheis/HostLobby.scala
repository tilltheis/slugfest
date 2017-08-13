package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.tilltheis.HostLobby.{StartGame, StopServer}
import de.tilltheis.LocalClient.UserSettings
import org.scalajs.dom
import org.scalajs.dom.html

object HostLobby {
  def props(peerId: String,
            userName: String,
            gameServerProps: Props,
            networkerServerProps: ActorRef => Props,
            viewProps: Props,
            localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props,
            internetServerService: ActorRef): Props =
    Props(new HostLobby(peerId, userName, gameServerProps, networkerServerProps, viewProps, localClientProps, internetServerService))

  object LobbyType extends Enumeration {
    val Host, Guest = Value
  }

  private case object StopServer
  private case object StartGame
}

class HostLobby private(peerId: String,
                        userName: String,
                        gameServerProps: Props,
                        networkerServerProps: ActorRef => Props,
                        viewProps: Props,
                        localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props,
                        internetServerService: ActorRef) extends Actor with ActorLogging {
  private val lobbyWidget = dom.document.getElementById("lobbyWidget").asInstanceOf[html.Element]
  private val userList = dom.document.getElementById("lobbyUserList").asInstanceOf[html.OList]
  private val quitLobbyButton = dom.document.getElementById("quitLobbyButton").asInstanceOf[html.Button]
  private val startGameButton = dom.document.getElementById("startServerGameButton").asInstanceOf[html.Button]

  private val gameServer = context.actorOf(gameServerProps)
  private val networkerGameServer = context.actorOf(networkerServerProps(gameServer))

  internetServerService ! InternetServerService.PublishServer(peerId, userName)
  private val userSettings = Set(
    LocalClient.UserSettings(userName, User.KeyCode.Left, User.KeyCode.Right))
  private val localClient = context.actorOf(localClientProps(gameServer, self, userSettings), "localClient")
  gameServer ! Server.Join(localClient, userSettings map (_.name))

  override def preStart(): Unit = {
    lobbyWidget.style.display = "flex"
    startGameButton.style.display = "inline-block"
    quitLobbyButton.onclick = _ => self ! StopServer
    startGameButton.onclick = _ => self ! StartGame
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def postStop(): Unit = {
    internetServerService ! InternetServerService.Unpublish(peerId)
    lobbyWidget.style.display = "none"
  }

  def receiveInLobby: Receive = {
    case StopServer =>
      context.stop(self)

    case Server.UserJoined(name) =>
      val listItem = dom.document.createElement("li")
      listItem.innerHTML = escapeHtml(name)
      userList.appendChild(listItem)

    case StartGame =>
      lobbyWidget.style.display = "none"

      context.parent ! Server.StartGame
      gameServer ! Server.StartGame

      context.become(receiveInGame(context.actorOf(viewProps)))
  }

  def receiveInGame(view: ActorRef): Receive = {
    case gameState: Game.GameState =>
      view ! gameState
  }


  override def receive: Receive = receiveInLobby

}
