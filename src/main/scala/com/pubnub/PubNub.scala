package com.pubnub

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("PubNub")
private class PubNubImpl(options: js.Any) extends js.Object {
  def this(publishKey: String, subscribeKey: String) =
    this(js.Dynamic.literal(publishKey = publishKey, subscribeKey = subscribeKey))

  def publish(options: js.Any, callback: js.Function2[PubNubPublishStatus, js.Any, Unit]): Unit = js.native
  def addListener(options: js.Any): js.Any = js.native
  def subscribe(options: js.Any): js.Any = js.native
  def unsubscribe(options: js.Any): js.Any = js.native
}

@js.native
private trait PubNubPublishStatus extends js.Object {
  def error: Boolean = js.native
}

@js.native
private trait PubNubStatusEvent extends js.Object {
  def category: String = js.native
  def affectedChannels: js.Array[String] = js.native
}

@js.native
private trait PubNubMessageEvent extends js.Object {
  def channel : String = js.native
  def message: js.Any = js.native
}

object PubNub {
  def apply(publishKey: String, subscribeKey: String): PubNub =
    new PubNub(new PubNubImpl(js.Dynamic.literal(publishKey = publishKey, subscribeKey = subscribeKey)))
}

class PubNub(pubNub: PubNubImpl) {
  def publish(channel: PubNubChannel, message: js.Any): Unit = {
    pubNub.publish(js.Dynamic.literal(channel = channel.name, message = message, storeInHistory = false), { (status, _response) =>
      if (status.error) {
        System.err.println(s"Could not publish ${JSON.stringify(message)} to $channel: ${JSON.stringify(status)}")
      }
    })
  }

  def onChannelConnect(f: PubNubChannel => Unit): Unit = {
    pubNub.addListener(js.Dynamic.literal(status = { statusEvent: PubNubStatusEvent =>
      if (statusEvent.category == "PNConnectedCategory") {
        f(PubNubChannel(statusEvent.affectedChannels.head)) // we only allow joining one channel at a time
      }
    }))
  }

  def onMessage(f: (PubNubChannel, js.Any) => Unit): Unit = {
    pubNub.addListener(js.Dynamic.literal(message = { messageEvent: PubNubMessageEvent =>
        f(PubNubChannel(messageEvent.channel), messageEvent.message)
    }))
  }

  def subscribe(channel: PubNubChannel): Unit = {
    pubNub.subscribe(js.Dynamic.literal(channels = js.Array(channel.name)))
  }

  def unsubscribe(channel: PubNubChannel): Unit = {
    pubNub.subscribe(js.Dynamic.literal(channels = mutable.Seq(channel.name)))
  }
}

case class PubNubChannel private(name: String)
object PubNubChannel {
  private val forbiddenChars = Set(',', '/', '\\', '.', '*', ':')
  def create(name: String): Option[PubNubChannel] =
    Some(name) filterNot (_ exists forbiddenChars.contains) map PubNubChannel.apply
}