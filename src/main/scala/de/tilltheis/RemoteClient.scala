package de.tilltheis

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object RemoteClient {
  def props(gameServer: ActorRef, networkerServer: ActorRef): Props =
    Props(new RemoteClient(gameServer, networkerServer))
}

class RemoteClient private (gameServer: ActorRef, networkerServer: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case playerAction: Game.PlayerAction => gameServer ! playerAction
    case gameState: Game.GameState => networkerServer ! gameState
  }
}
