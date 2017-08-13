package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.pubnub.{PubNub, PubNubChannel}
import de.tilltheis.InternetServerService._
import org.scalajs.dom.ext

import scala.concurrent.duration._

object InternetServerService {
  def props(pubNubChannelName: PubNubChannel, pubNub: PubNub): Props = Props(new InternetServerService(pubNubChannelName, pubNub))

  case class InternetServer(peerId: String, name: String)
  case class DiscoveredServers(servers: Set[InternetServer])

  case class PublishServer(peerId: String, name: String)
  case class Unpublish(peerId: String)
  case object SubscribeToCurrentServerList
  case object UnsubscribeFromCurrentServerList

  private case object Publish
  private case class Discover(server: InternetServer)
  private case object UpdateCacheAndNotify

  private type DateTimeMillis = Long
  private case class State(publishedServers: Map[String, String], discoveredServers: Map[InternetServer, DateTimeMillis], subscribers: Set[ActorRef])
}

class InternetServerService private(pubNubChannelName: PubNubChannel, pubNub: PubNub) extends Actor with ActorLogging {
  import context.dispatcher


  override def preStart(): Unit = {
    pubNub.subscribe(pubNubChannelName)
    pubNub.onMessage {
      case (`pubNubChannelName`, message) =>
        import JsonCodec.Implicits._
        JsonCodec.decodeJson[InternetServer](message) foreach (self ! Discover(_))

      case _ => ()
    }
  }

  override def postRestart(reason: Throwable): Unit = ()

  self ! Publish
  self ! UpdateCacheAndNotify

  private def receive(state: State): Receive = {
    case SubscribeToCurrentServerList =>
      context.become(receive(state.copy(subscribers = state.subscribers + sender)))

    case UnsubscribeFromCurrentServerList =>
      context.become(receive(state.copy(subscribers = state.subscribers - sender)))

    case PublishServer(peerId, name) =>
      context.become(receive(state.copy(publishedServers = state.publishedServers + (peerId -> name))))

    case Unpublish(peerId) =>
      context.become(receive(state.copy(publishedServers = state.publishedServers - peerId)))

    case Publish =>
      import JsonCodec.Implicits._
      state.publishedServers map InternetServer.tupled foreach { server =>
        pubNub.publish(pubNubChannelName, JsonCodec.encodeJson(server))
      }
      context.system.scheduler.scheduleOnce(1.second, self, Publish)

    case Discover(server) =>
      val now = System.currentTimeMillis()
      val newDiscoveredServers = state.discoveredServers + (server -> now)
      context.become(receive(state.copy(discoveredServers = newDiscoveredServers)))

    case UpdateCacheAndNotify =>
      val now = System.currentTimeMillis()
      val allServers = state.discoveredServers
      val aliveServers = allServers filter (_._2 >= now - 1500)
      state.subscribers foreach (_ ! DiscoveredServers(aliveServers.keySet))
      context.system.scheduler.scheduleOnce(1.second, self, UpdateCacheAndNotify)
      context.become(receive(state.copy(discoveredServers = aliveServers)))
  }

  override def receive: Receive = receive(State(Map.empty, Map.empty, Set.empty))
}
