package de.tilltheis

import org.scalajs.dom
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import de.tilltheis.InternetServerService.InternetServer
import de.tilltheis.MainMenu.{HostServer, JoinServer}
import org.scalajs.dom.html

object MainMenu {
  def props(hostLobbyProps: (String, ActorRef) => Props, guestLobbyProps: (String, String, String, ActorRef) => Props, internetServerServiceProps: Props) =
    Props(new MainMenu(hostLobbyProps, guestLobbyProps, internetServerServiceProps))

  private object HostServer
  private case class JoinServer(server: InternetServer)
}

class MainMenu private(hostLobbyProps: (String, ActorRef) => Props,
                       guestLobbyProps: (String, String, String, ActorRef) => Props,
                       internetServerServiceProps: Props) extends Actor with ActorLogging {
  private val hostServerButton = dom.document.getElementById("hostServerButton").asInstanceOf[html.Button]
  private val userNameInput = dom.document.getElementById("userNameInput").asInstanceOf[html.Input]
  private val serverListWidget = dom.document.getElementById("serverListWidget").asInstanceOf[html.Element]
  private val mainMenuWidget = dom.document.getElementById("mainMenuWidget").asInstanceOf[html.Element]

  private val internetServerService = context.actorOf(internetServerServiceProps)

  internetServerService ! InternetServerService.SubscribeToCurrentServerList

  override def preStart(): Unit = {
    hostServerButton.onclick = _ => self ! HostServer
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def postStop(): Unit = {
    internetServerService ! InternetServerService.UnsubscribeFromCurrentServerList
  }

  private def receiveInMainMenu: Receive = {
    case HostServer =>
      val lobby = context.actorOf(hostLobbyProps(userNameInput.value, internetServerService))
      context.watch(lobby)
      context.become(receiveInLobby)

    case JoinServer(server) =>
      val lobby = context.actorOf(guestLobbyProps(server.peerId, userNameInput.value, server.name, internetServerService))
      context.watch(lobby)
      context.become(receiveInLobby)

    case discoveredServers: InternetServerService.DiscoveredServers =>
      // todo: use real templating
      //  <button type="button" class="list-group-item">Cras justo odio</button>
      serverListWidget.innerHTML = ""
      discoveredServers.servers foreach { server =>
        val button = dom.document.createElement("button").asInstanceOf[html.Button]
        button.classList.add("list-group-item")
        button.innerHTML = escapeHtml(server.name)
        button.onclick = { _ =>
          log.info("join server {} ({})", server.name, server.peerId)
          self ! JoinServer(server)
        }
        serverListWidget.appendChild(button)
      }
  }

  private def receiveInLobby: Receive = {
    case Terminated(_lobby) =>
      context.become(receiveInMainMenu)

    case Game.Started =>
      mainMenuWidget.style.display = "none"
  }

  override def receive: Receive = receiveInMainMenu
}
