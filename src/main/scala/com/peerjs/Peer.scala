package com.peerjs

import scala.scalajs.js
import js.annotation._
import js.|

//@js.native
//object Peer extends js.Object {
///* ??? ConstructorMember(FunSignature(List(),List(FunParam(Ident(id),false,Some(TypeRef(CoreType(string),List()))), FunParam(Ident(options),true,Some(TypeRef(QualifiedTypeName(List(Ident(PeerJs)),TypeName(PeerJSOption)),List())))),Some(TypeRef(QualifiedTypeName(List(Ident(PeerJs)),TypeName(Peer)),List())))) */
///* ??? ConstructorMember(FunSignature(List(),List(FunParam(Ident(options),false,Some(TypeRef(QualifiedTypeName(List(Ident(PeerJs)),TypeName(PeerJSOption)),List())))),Some(TypeRef(QualifiedTypeName(List(Ident(PeerJs)),TypeName(Peer)),List())))) */
//}

@js.native
trait PeerJSOption extends js.Object {
  var key: js.UndefOr[String] = js.native
  var host: js.UndefOr[String] = js.native
  var port: js.UndefOr[Double] = js.native
  var path: js.UndefOr[String] = js.native
  var secure: js.UndefOr[Boolean] = js.native
//  var config: js.UndefOr[RTCPeerConnectionConfig] = js.native
  var debug: js.UndefOr[Double] = js.native
}

object PeerJSOption {
  def apply(
    key: js.UndefOr[String] = js.undefined,
    host: js.UndefOr[String] = js.undefined,
    port: js.UndefOr[Double] = js.undefined,
    path: js.UndefOr[String] = js.undefined,
    secure: js.UndefOr[Boolean] = js.undefined,
    debug: js.UndefOr[Double] = js.undefined): PeerJSOption = {
    val obj = js.Dynamic.literal()
    if (key.isDefined) obj.key = key
    if (host.isDefined) obj.host = host
    if (port.isDefined) obj.port = port
    if (path.isDefined) obj.path = path
    if (secure.isDefined) obj.secure = secure
    if (debug.isDefined) obj.debug = debug
    obj.asInstanceOf[PeerJSOption]
  }

}

@js.native
trait PeerConnectOption extends js.Object {
  var label: String = js.native
  var metadata: js.Any = js.native
  var serialization: String = js.native
  var reliable: Boolean = js.native
}

@js.native
trait DataConnection extends js.Object {
  def send(data: js.Any): Unit = js.native
  def close(): Unit = js.native
  def on(event: String, cb: js.Function): Unit = js.native
  def off(event: String, fn: js.Function, once: Boolean = ???): Unit = js.native
//  var dataChannel: RTCDataChannel = js.native
  var label: String = js.native
  var metadata: js.Any = js.native
  var open: Boolean = js.native
  var peerConnection: js.Any = js.native
  var peer: String = js.native
  var reliable: Boolean = js.native
  var serialization: String = js.native
  var `type`: String = js.native
  var buffSize: Double = js.native
}

@js.native
trait MediaConnection extends js.Object {
  def answer(stream: js.Any = ???): Unit = js.native
  def close(): Unit = js.native
  def on(event: String, cb: js.Function0[Unit]): Unit = js.native
  def off(event: String, fn: js.Function, once: Boolean = ???): Unit = js.native
  var open: Boolean = js.native
  var metadata: js.Any = js.native
  var peer: String = js.native
  var `type`: String = js.native
}

@js.native
trait utilSupportsObj extends js.Object {
  var audioVideo: Boolean = js.native
  var data: Boolean = js.native
  var binary: Boolean = js.native
  var reliable: Boolean = js.native
}

@js.native
trait util extends js.Object {
  var browser: String = js.native
  var supports: utilSupportsObj = js.native
}

@js.native
@JSGlobal
class Peer(_id: js.UndefOr[String], options: js.UndefOr[PeerJSOption]) extends js.Object {
  def this(id: String) = this(id, js.undefined)
  def this(options: PeerJSOption) = this(js.undefined, options)
  def this() = this(js.undefined, js.undefined)

  def connect(id: String, options: PeerConnectOption = ???): DataConnection = js.native
  def call(id: String, stream: js.Any, options: js.Any = ???): MediaConnection = js.native
  def on(event: String, cb: js.Function): Unit = js.native
  def off(event: String, fn: js.Function, once: Boolean = ???): Unit = js.native
  def disconnect(): Unit = js.native
  def reconnect(): Unit = js.native
  def destroy(): Unit = js.native
  def getConnection(peer: Peer, id: String): js.Dynamic = js.native
  def listAllPeers(callback: js.Function1[js.Array[String], Unit]): Unit = js.native
  var id: String = js.native
  var connections: js.Any = js.native
  var disconnected: Boolean = js.native
  var destroyed: Boolean = js.native
}
