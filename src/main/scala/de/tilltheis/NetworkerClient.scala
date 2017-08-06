package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.peerjs.{DataConnection, Peer, PeerJSOption}
import de.tilltheis.Game.{GameState, PlayerAction}
import de.tilltheis.NetworkerClient.{NewConnection, RemoteCommand}
import de.tilltheis.NetworkerServer.RemoteJoin

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.util.{Failure, Success}

object NetworkerClient {
  def props(serverPeerId: String, peerJsApiKey: String): Props = Props(new NetworkerClient(serverPeerId, peerJsApiKey))

  private case class NewConnection(connection: DataConnection)
  private case class RemoteCommand(command: js.Any)
}

class NetworkerClient(serverPeerId: String, peerJsApiKey: String) extends Actor with ActorLogging {
  private val peer = new Peer(PeerJSOption(key = peerJsApiKey))

  peer.on("error", (error: js.Any) => {
    log.error("peer error {}", error)
  })

  peer.on("open", (id: String) => {
    log.info("listening on id {}", id)

    val connection = peer.connect(serverPeerId)

    connection.on("error", (error: js.Any) => {
      log.error("connection error {}", error)
    })

    connection.on("open", () => {
      log.info("connection open")

      connection.on("data", (data: js.Any) => {
        self ! RemoteCommand(data)
      })

      // we need to wait before sending the first message to give the server time to open the connection
      context.system.scheduler.scheduleOnce(1.second, self, NewConnection(connection))(context.dispatcher)
    })
  })


  private var connection: Option[DataConnection] = None
  private var cachedJoins = Seq.empty[Server.Join]
  private var clients = Map.empty[ActorRef, Set[String]]


  override def receive: Receive = {
    case NewConnection(conn) =>
      connection = Some(conn)
      flushClientCache()

    case join: Server.Join =>
      connection.fold {
        cachedJoins :+= join
      } {
        sendJoin(_, join)
      }

    case action: PlayerAction =>
      import JsonCodec.Implicits._
      val json = JsonCodec.encodeJson(action)
      connection foreach (_.send(json))

    case RemoteCommand(json) =>
      import JsonCodec.Implicits._
      JsonCodec.decodeJson[GameState](json) match {
        case Success(gameState) =>
          clients.keys foreach (_ ! gameState)
        case Failure(t) => log.error(t, "could not parse remote command {}", JSON.stringify(json))
      }

  }

  private def sendJoin(conn: DataConnection, join: Server.Join): Unit = {
    import JsonCodec.Implicits._
    conn.send(JsonCodec.encodeJson(RemoteJoin(peer.id, join.names)))
  }

  private def flushClientCache(): Unit = {
    connection foreach { conn =>
      cachedJoins foreach (sendJoin(conn, _))
      clients ++= cachedJoins map (j => j.client -> j.names)
      cachedJoins = Seq.empty
    }
  }
}
