package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.peerjs.{DataConnection, Peer, PeerJSOption}
import de.tilltheis.Game.PlayerAction
import de.tilltheis.NetworkerServer._

import scala.concurrent.duration._
import scala.scalajs.js

object NetworkerServer {
  def props(server: ActorRef, peerJsApiKey: String, serverPeerId: String): Props =
    Props(new NetworkerServer(server, peerJsApiKey, serverPeerId))

  case class ServerStarted(peerId: String)

  case class NewConnection(connection: DataConnection)
  case class RemoteJoin(peerId: String, users: Set[String])
  case class RemoteMessage(peerId: String, message: Any)
}

class NetworkerServer(server: ActorRef, peerJsApiKey: String, serverPeerId: String) extends Actor with ActorLogging {
  val peer = new Peer(serverPeerId, PeerJSOption(key = peerJsApiKey))

  peer.on("error", (error: js.Any) => {
    log.error("peer error {}", error)
  })

  peer.on("open", (id: String) => {
    log.info("listening on id {}", id)

    context.parent ! ServerStarted(id)

    peer.on("connection", (connection: DataConnection) => {
      log.info("connection request from {}", connection.peer)

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

      // the "open" event is so unreliable we have to poll for it. probably because the event listener registration
      // sometimes happens after the event has been fired and the event listener isn't being triggered in that case
      lazy val openConnectionPoller: Cancellable = context.system.scheduler.schedule(0.seconds, 100.millis) {
        if (connection.open) {
          log.info("connection opened to {}", connection.peer)
          openConnectionPoller.cancel()
          self ! NewConnection(connection)
        }
      }(context.dispatcher)
      openConnectionPoller // evaluate lazy val
    })
  })

  private var connections = Set.empty[DataConnection]
  private var clients = Map.empty[String, ActorRef]
  private var userNames = Set.empty[String]

  // cache full bodies here because network messages only contain deltas
  private val initialCachedBodies = Map.empty[String, List[Point]].withDefaultValue(Nil)
  private var cachedBodies = initialCachedBodies
  private var cachedGameStatus: Game.Status = Game.Running

  override def receive: Receive = {
    case NewConnection(conn) =>
      connections += conn

    case RemoteMessage(peerId, join: RemoteJoin) =>
      val client = context.actorOf(RemoteClient.props(server, self))
      clients += peerId -> client
      server ! Server.Join(client, join.users)
      userNames ++= join.users
      import JsonCodec.Implicits._
      connections find (_.peer == peerId) foreach { con =>
        userNames foreach (u => con.send(JsonCodec.encodeJson(Server.UserJoined(u))))
      }

    case RemoteMessage(peerId, playerAction: PlayerAction) =>
      clients(peerId) ! playerAction

    case Server.UserJoined(name) =>
      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(Server.UserJoined(name))))

    case Server.StartGame =>
      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(Server.StartGame)))

    case gameState: Game.GameState =>
      // only send possibly changed data

      // game restart?
      if (cachedGameStatus.isInstanceOf[Game.Finished] && gameState.state == Game.Running) {
        cachedBodies = initialCachedBodies
      }
      cachedGameStatus = gameState.state

      val optimizedPlayers = gameState.players map { player =>
        player.copy(body = player.body.headOption.toList filterNot cachedBodies(player.name).headOption.contains)
      }
      val optimizedGameState = gameState.copy(players = optimizedPlayers)

      gameState.players foreach (p => cachedBodies += p.name -> p.body)

      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(optimizedGameState)))
  }

  private def jsonToServerMessage(json: js.Any): Option[Any] = {
    import JsonCodec.Implicits._
    (JsonCodec.decodeJson[RemoteJoin](json) orElse JsonCodec.decodeJson[PlayerAction](json)).toOption
  }

}
