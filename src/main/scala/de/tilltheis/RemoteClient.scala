package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object RemoteClient {
  def props(gameServer: ActorRef, networkerServer: ActorRef): Props =
    Props(new RemoteClient(gameServer, networkerServer))
}

class RemoteClient private (gameServer: ActorRef, networkerServer: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case x: Game.SteerPlayer => gameServer ! x

    case x: Server.StartGame.type => networkerServer ! x
    case x: Server.UserJoined => networkerServer ! x
    case x: Game.GameState => networkerServer ! x
  }
}
