package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.peerjs.{DataConnection, Peer, PeerJSOption}
import de.tilltheis.Game.PlayerAction
import de.tilltheis.NetworkerServer.{NewConnection, RemoteJoin, RemoteMessage, ServerStarted}

import scala.scalajs.js

object NetworkerServer {
  def props(server: ActorRef, peerJsApiKey: String): Props = Props(new NetworkerServer(server, peerJsApiKey))

  case class ServerStarted(peerId: String)

  case class NewConnection(connection: DataConnection)
  case class RemoteJoin(peerId: String, users: Set[String])
  case class RemoteMessage(peerId: String, message: Any)
}

class NetworkerServer(server: ActorRef, peerJsApiKey: String) extends Actor with ActorLogging {
  val peer = new Peer(PeerJSOption(key = peerJsApiKey))

  peer.on("error", (error: js.Any) => {
    log.error("peer error {}", error)
  })

  peer.on("open", (id: String) => {
    log.info("listening on id {}", id)

    context.parent ! ServerStarted(id)

    peer.on("connection", (connection: DataConnection) => {
      log.info("new connection from {}", connection.peer)

      self ! NewConnection(connection)

      connection.on("error", (error: js.Any) => {
        log.error("connection error {}", error)
      })

      connection.on("data", (data: js.Any) => {
        jsonToServerMessage(data).fold {
          log.error("received unknown remote message {}", JsonCodec.jsonString(data))
        } {
          self ! RemoteMessage(connection.peer, _)
        }
      })
    })
  })

  private var connections = Set.empty[DataConnection]
  private var clients = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case NewConnection(conn) =>
      connections += conn

    case RemoteMessage(peerId, join: RemoteJoin) =>
      val client = context.actorOf(RemoteClient.props(server, self))
      clients += peerId -> client
      server ! Server.Join(client, join.users)

    case RemoteMessage(peerId, playerAction: PlayerAction) =>
      clients(peerId) ! playerAction

    case gameState: Game.GameState =>
      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(gameState)))
  }

  private def jsonToServerMessage(json: js.Any): Option[Any] = {
    import JsonCodec.Implicits._
    (JsonCodec.decodeJson[RemoteJoin](json) orElse JsonCodec.decodeJson[PlayerAction](json)).toOption
  }

}
