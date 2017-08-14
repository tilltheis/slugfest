package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.tilltheis.HostLobby.{StartGame, StopServer}
import de.tilltheis.LocalClient.UserSettings
import org.scalajs.dom
import org.scalajs.dom.html

object HostLobby {
  def props(peerId: String,
            userName: String,
            serverProps: Props,
            viewProps: Props,
            localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props,
            internetServerService: ActorRef): Props =
    Props(new HostLobby(peerId, userName, serverProps, viewProps, localClientProps, internetServerService))

  // internal
  private case object StopServer
  private case object StartGame
}

class HostLobby private(peerId: String,
                        userName: String,
                        serverProps: Props,
                        viewProps: Props,
                        localClientProps: (ActorRef, ActorRef, Set[UserSettings]) => Props,
                        internetServerService: ActorRef) extends Actor with ActorLogging {
  private val lobbyWidget = dom.document.getElementById("lobbyWidget").asInstanceOf[html.Element]
  private val userList = dom.document.getElementById("lobbyUserList").asInstanceOf[html.OList]
  private val quitLobbyButton = dom.document.getElementById("quitLobbyButton").asInstanceOf[html.Button]
  private val startGameButton = dom.document.getElementById("startServerGameButton").asInstanceOf[html.Button]

  private val server = context.actorOf(serverProps)

  internetServerService ! InternetServerService.PublishServer(peerId, userName)
  private val userSettings = Set(
    LocalClient.UserSettings(userName, User.KeyCode.Left, User.KeyCode.Right))
  userSettings foreach { settings =>
    val user = User.props(settings.name, settings.leftKey, settings.rightKey, server)
    context.actorOf(user)
  }
  userSettings foreach (us => server ! Server.JoinPlayer(us.name))

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
    // commands

    case StartGame =>
      server ! Server.StartGame

    case StopServer =>
      context.stop(self)


    // events

    case Server.PlayerJoined(name) =>
      val listItem = dom.document.createElement("li")
      listItem.innerHTML = escapeHtml(name)
      userList.appendChild(listItem)

    case Server.GameStarted =>
      lobbyWidget.style.display = "none"
      context.parent ! Server.StartGame
      context.become(receiveInGame(context.actorOf(viewProps)))
  }

  def receiveInGame(view: ActorRef): Receive = {
    case Game.GameStateChanged(gameState) =>
      view ! gameState
  }


  override def receive: Receive = receiveInLobby

}
