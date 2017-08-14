package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.peerjs.{DataConnection, Peer, PeerJSOption}
import de.tilltheis.Game.{GameState, SteerPlayer}
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

  // cache full bodies here because network messages only contain deltas
  private val initialCachedBodies = Map.empty[String, List[Point]].withDefaultValue(Nil)
  private var cachedBodies = initialCachedBodies
  private var cachedGameStatus: Game.Status = Game.Running

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

    case action: SteerPlayer =>
      import JsonCodec.Implicits._
      val json = JsonCodec.encodeJson(action)
      connection foreach (_.send(json))

    case RemoteCommand(json) =>
      jsonToMessage(json).fold(log.error("could not parse remote command {}", JSON.stringify(json))) {
        case Server.StartGame =>
          context.parent ! Server.StartGame

        case optimizedGameState: GameState =>
          // game restart?
          if (cachedGameStatus.isInstanceOf[Game.Finished] && optimizedGameState.state == Game.Running) {
            cachedBodies = initialCachedBodies
          }
          cachedGameStatus = optimizedGameState.state

          val players = optimizedGameState.players map { player =>
            player.copy(body = player.body.headOption.fold(cachedBodies(player.name))(_ :: cachedBodies(player.name)))
          }
          val gameState = optimizedGameState.copy(players = players)

          players foreach (p => cachedBodies += p.name -> p.body)
          clients.keys foreach (_ ! gameState)

        case Server.UserJoined(name) =>
          log.info("user joined: {}", name)
          context.parent ! Server.UserJoined(name)

        case x => log.error("unhandled remote command {}", x)
      }

  }

  private def jsonToMessage(json: js.Any): Option[Any] = {
    import JsonCodec.Implicits._
    (JsonCodec.decodeJson[Server.StartGame.type](json) orElse
      JsonCodec.decodeJson[GameState](json) orElse
      JsonCodec.decodeJson[Server.UserJoined](json)).toOption
  }

  private def sendJoin(conn: DataConnection, join: Server.Join): Unit = {
    import JsonCodec.Implicits._
    conn.send(JsonCodec.encodeJson(RemoteJoin(peer.id, join.names)))
    clients += join.client -> join.names
  }

  private def flushClientCache(): Unit = {
    connection foreach { conn =>
      cachedJoins foreach (sendJoin(conn, _))
      cachedJoins = Seq.empty
    }
  }
}
