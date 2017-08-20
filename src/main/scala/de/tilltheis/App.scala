package de.tilltheis

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import com.pubnub.{PubNub, PubNubChannel}
import com.typesafe.config.ConfigFactory
import de.tilltheis.LocalClient.UserSettings

import scala.concurrent.duration._

object App {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load().withFallback(akkajs.Config.default)
    val system = ActorSystem("app", config)

    system.scheduler.scheduleOnce(0.millis) {
      val peerJsApiKey = config.getString("peerJs.apiKey")
      val peerId = UUID.randomUUID().toString
      val pubNubPublishKey = config.getString("pubNub.publishKey")
      val pubNubSubscribeKey = config.getString("pubNub.subscribeKey")
      val pubNubChannelName = PubNubChannel.create(config.getString("pubNub.serverTopic")).get

      val mainModule = new AppModule(peerJsApiKey, peerId, pubNubPublishKey, pubNubSubscribeKey, pubNubChannelName)
      system.actorOf(mainModule.mainMenuProps)
    }(system.dispatcher)
  }
}

class AppModule(peerJsApiKey: String, peerId: String, pubNubPublishKey: String, pubNubSubscribeKey: String, pubNubChannel: PubNubChannel) {
  lazy val pubNub = PubNub(publishKey = pubNubPublishKey, subscribeKey = pubNubSubscribeKey)
  def gameProps(players: Set[String]): Props = Game.props(Dimensions(500, 500), players)
  lazy val gameServerProps: Props = Server.props(gameProps)
  def networkerServerProps: Props = NetworkerServer.props(gameServerProps, peerJsApiKey, peerId)
  def networkerClientProps(serverPeerId: String): Props = NetworkerClient.props(serverPeerId, peerJsApiKey)
  lazy val viewProps: Props = View.props(Dimensions(500, 500))
  def localClientProps(server: ActorRef, view: ActorRef, userSettings: Set[UserSettings]): Props = LocalClient.props(server, view, userSettings)
  def hostLobbyProps(userName: String, internetServerService: ActorRef): Props = HostLobby.props(peerId, userName, networkerServerProps, viewProps, localClientProps, internetServerService)
  def guestLobbyProps(serverPeerId: String, userName: String, serverName: String, internetServerService: ActorRef): Props = GuestLobby.props(serverPeerId, userName, serverName, networkerClientProps, viewProps, localClientProps)
  lazy val internetServerServiceProps: Props = InternetServerService.props(pubNubChannel, pubNub)
  lazy val mainMenuProps: Props = MainMenu.props(hostLobbyProps, guestLobbyProps, internetServerServiceProps)
}
