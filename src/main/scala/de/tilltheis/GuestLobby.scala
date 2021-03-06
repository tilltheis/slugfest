package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, Props}
import org.scalajs.dom
import org.scalajs.dom.html

object GuestLobby {
  def props(serverPeerId: String,
            userName: String,
            serverName: String,
            networkerClientProps: String => Props,
            viewProps: Props): Props =
    Props(new GuestLobby(serverPeerId, userName, serverName, networkerClientProps, viewProps))
}

class GuestLobby private(serverPeerId: String,
                         userName: String,
                         serverName: String,
                         networkerClientProps: String => Props,
                         viewProps: Props) extends Actor with ActorLogging {
  private val lobbyWidget = dom.document.getElementById("lobbyWidget").asInstanceOf[html.Element]
  private val userList = dom.document.getElementById("lobbyUserList").asInstanceOf[html.OList]
  private val quitLobbyButton = dom.document.getElementById("quitLobbyButton").asInstanceOf[html.Button]
  private val startGameButton = dom.document.getElementById("startServerGameButton").asInstanceOf[html.Button]

  private val networkerClient = context.actorOf(networkerClientProps(serverPeerId))
  private val userSettings = Set(
    UserSettings(userName, User.KeyCode.Left, User.KeyCode.Right))
  userSettings foreach { settings =>
    val user = User.props(settings.name, settings.leftKey, settings.rightKey, self)
    context.actorOf(user)
  }
  userSettings foreach (us => networkerClient ! Server.JoinPlayer(us.name))

  userList.innerHTML = ""

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
    case Server.PlayerJoined(name) =>
      val listItem = dom.document.createElement("li")
      listItem.innerHTML = escapeHtml(name)
      userList.appendChild(listItem)

    case started@Game.Started =>
      lobbyWidget.style.display = "none"
      context.parent ! started
      context.become(receiveInGame(context.actorOf(viewProps)))
  }

  def receiveInGame(view: ActorRef): Receive = {
    case Game.GameStateChanged(gameState) =>
      view ! gameState

    case playerAction: Game.SteerPlayer => networkerClient ! playerAction
  }


  override def receive: Receive = receiveInLobby

}
