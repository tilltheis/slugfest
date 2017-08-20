package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.peerjs.{DataConnection, Peer, PeerJSOption}
import de.tilltheis.Game.SteerPlayer
import de.tilltheis.NetworkerServer._

import scala.concurrent.duration._
import scala.scalajs.js

object NetworkerServer {
  def props(serverProps: Props, peerJsApiKey: String, serverPeerId: String): Props =
    Props(new NetworkerServer(serverProps, peerJsApiKey, serverPeerId))

  case class ServerStarted(peerId: String)

  case class NewConnection(connection: DataConnection)
  case class RemoteJoin(peerId: String, player: String)
  case class RemoteMessage(peerId: String, message: Any)
}

class NetworkerServer(serverProps: Props, peerJsApiKey: String, serverPeerId: String) extends Actor with ActorLogging {
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

  private val server = context.actorOf(serverProps)

  private var connections = Set.empty[DataConnection]
  private var clients = Map.empty[String, ActorRef]
  private var playerNames = Set.empty[String]

  // cache full bodies here because network messages only contain deltas
  private val initialCachedBodies = Map.empty[String, List[Point]].withDefaultValue(Nil)
  private var cachedBodies = initialCachedBodies
  private var cachedGameStatus: Game.Status = Game.Started

  override def receive: Receive = {
    // commands

    case join: Server.JoinPlayer =>
      server ! join

    case start@Server.StartGame =>
      server ! start

    case steer: Game.SteerPlayer =>
      server ! steer


    // events

    case started@Game.Started =>
      context.parent ! started
      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(started)))

    case joined@Server.PlayerJoined(name) =>
      playerNames += name
      context.parent ! joined
      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(Server.PlayerJoined(name))))

    case changed@Game.GameStateChanged(gameState) =>
      context.parent ! changed

      // only send possibly changed data

      // game restart?
      if (cachedGameStatus.isInstanceOf[Game.Finished] && gameState.state == Game.Started) {
        cachedBodies = initialCachedBodies
      }
      cachedGameStatus = gameState.state

      val optimizedPlayers = gameState.players map { player =>
        player.copy(body = player.body.headOption.toList filterNot cachedBodies(player.name).headOption.contains)
      }
      val optimizedGameState = gameState.copy(players = optimizedPlayers)

      gameState.players foreach (p => cachedBodies += p.name -> p.body)

      import JsonCodec.Implicits._
      connections foreach (_.send(JsonCodec.encodeJson(Game.GameStateChanged(optimizedGameState))))


    // internal
    case NewConnection(conn) =>
      connections += conn

    case RemoteMessage(peerId, join: RemoteJoin) =>
      val client = context.actorOf(RemoteClient.props(server, self))
      clients += peerId -> client
      server ! Server.JoinPlayer(join.player)

      import JsonCodec.Implicits._
      connections find (_.peer == peerId) foreach { con =>
        playerNames foreach (u => con.send(JsonCodec.encodeJson(Server.PlayerJoined(u))))
      }

      playerNames += join.player

    case RemoteMessage(peerId, playerAction: SteerPlayer) =>
      clients(peerId) ! playerAction
  }

  private def jsonToServerMessage(json: js.Any): Option[Any] = {
    import JsonCodec.Implicits._
    (JsonCodec.decodeJson[RemoteJoin](json) orElse JsonCodec.decodeJson[SteerPlayer](json)).toOption
  }

}
