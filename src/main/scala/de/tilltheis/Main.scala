package de.tilltheis

import java.util.UUID

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import de.tilltheis.Main.{HostServer, JoinServer}
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, html}
import org.scalajs.dom.html.Canvas

import scala.concurrent.duration._

class Main private(canvas: Canvas, serverNameInput: html.Input, peerJsApiKey: String) extends Actor with ActorLogging {
  val dimensions = Dimensions(500, 500)
  var view: ActorRef = _
  private var server: Option[ActorRef] = None

  override def preStart(): Unit = {
    view = context.actorOf(View.props(dimensions, canvas), "view")
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def receive: Receive = {
    case HostServer =>
      log.info("SERVER")

      val server = context.actorOf(Server.props, "server")
      val userSettings = Set(
        LocalClient.UserSettings("Host", User.KeyCode.Left, User.KeyCode.Right))
      val localClient = context.actorOf(LocalClient.props(server, view, userSettings, canvas), "localClient")
      server ! Server.Join(localClient, userSettings map (_.name))

      val networkerServer = context.actorOf(NetworkerServer.props(server, peerJsApiKey), "networkerServer")

      this.server = Some(server)


    case NetworkerServer.ServerStarted(name) =>
      serverNameInput.value = name


    case Main.StartGame =>
      this.server foreach (_ ! Server.StartGame)


    case JoinServer(serverName) =>
      log.info("CLIENT")

      val userName = UUID.randomUUID().toString.take(5)

      val networkerClient = context.actorOf(NetworkerClient.props(serverName, peerJsApiKey), "networkerClient")
      val userSettings = Set(
        LocalClient.UserSettings(userName, User.KeyCode.Left, User.KeyCode.Right))
      val localClient = context.actorOf(LocalClient.props(networkerClient, view, userSettings, canvas), "localClient")
      networkerClient ! Server.Join(localClient, userSettings map (_.name))
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case t: Throwable =>
      log.error(t, "Supervised exception.")
      super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
  }
}

object Main {
  def props(canvas: Canvas, serverNameInput: html.Input, peerJsApiKey: String): Props =
    Props(new Main(canvas, serverNameInput, peerJsApiKey))

  case object HostServer
  case object StartGame
  case class JoinServer(serverName: String)

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    implicit val system = ActorSystem("app", config)
    system.eventStream.setLogLevel(Logging.InfoLevel)

    import system.dispatcher

    system.scheduler.scheduleOnce(0.millis) {
      val canvas = dom.document.getElementById("gameCanvas").asInstanceOf[Canvas]
      val serverNameInput = dom.document.getElementById("serverServerName").asInstanceOf[html.Input]
      val peerJsApiKey = config.getString("peerJsApiKey")
      val main = system.actorOf(Main.props(canvas, serverNameInput, peerJsApiKey))

      dom.document.getElementById("startServer").addEventListener[MouseEvent]("click", { event =>
        main ! HostServer
      })
      dom.document.getElementById("joinServer").addEventListener[MouseEvent]("click", { event =>
        main ! JoinServer(dom.document.getElementById("clientServerName").asInstanceOf[html.Input].value)
      })
      dom.document.getElementById("startGame").addEventListener[MouseEvent]("click", { event =>
        main ! Main.StartGame
      })
    }
  }
}
