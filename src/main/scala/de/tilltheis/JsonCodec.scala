package de.tilltheis

import de.tilltheis.Game.{GameState, Player, PlayerAction, Status}
import de.tilltheis.NetworkerServer.RemoteJoin

import scala.scalajs.js
import scala.scalajs.js.{Dynamic, JSON}
import scala.util.{Failure, Success, Try}

trait JsonEncoder[A] {
  def apply(x: A): js.Any
  def toString(x: A): String = JSON.stringify(apply(x))
}
object JsonEncoder {
  def apply[A](encode: A => js.Any): JsonEncoder[A] = encode.apply
}

trait JsonDecoder[A] {
  def apply(json: js.Any): Try[A]
}
object JsonDecoder {
  def apply[A](decode: Dynamic => A): JsonDecoder[A] = json => try {
    Success(decode(json.asInstanceOf[js.Dynamic]))
  } catch {
    // catch everything because `(x: js.Dynamic).foo.asInstanceOf[Y]` will throw an undefined behavior exception...
    case e: Throwable => Failure(e)
  }
}

object JsonCodec {
  def decodeJson[A](json: js.Any)(implicit decode: JsonDecoder[A]): Try[A] = decode(json)
  def decodeJsonString[A](json: String)(implicit decode: JsonDecoder[A]): Try[A] = decode(JSON.parse(json))
  def encodeJson[A](x: A)(implicit encode: JsonEncoder[A]): js.Any = encode(x)
  def encodeJsonString[A](x: A)(implicit encode: JsonEncoder[A]): String = encode.toString(x)
  def jsonString(json: js.Any): String = JSON.stringify(json)

  object Implicits {
    implicit def optionEncoder[A](implicit encode: JsonEncoder[A]): JsonEncoder[Option[A]] =
      _.fold[js.Any](null)(encode.apply)
    implicit def optionDecoder[A](implicit decode: JsonDecoder[A]): JsonDecoder[Option[A]] =
      Option(_).fold[Try[Option[A]]](Success(None))(json => decode(json) map Some.apply)

    implicit def listEncoder[A](implicit encode: JsonEncoder[A]): JsonEncoder[List[A]] =
      xs => js.Array(xs map encode.apply: _*)
    implicit def listDecoder[A](implicit decode: JsonDecoder[A]): JsonDecoder[List[A]] =
      JsonDecoder(_.asInstanceOf[js.Array[js.Any]].map(decode(_).get).toList)

    implicit def seqEncoder[A](implicit encode: JsonEncoder[A]): JsonEncoder[Seq[A]] =
      xs => js.Array(xs map encode.apply: _*)
    implicit def seqDecoder[A](implicit decode: JsonDecoder[A]): JsonDecoder[Seq[A]] =
      JsonDecoder(_.asInstanceOf[js.Array[js.Any]].map(decode(_).get).toSeq)

    implicit def setEncoder[A](implicit encode: JsonEncoder[A]): JsonEncoder[Set[A]] =
      xs => js.Array(xs.toSeq map encode.apply: _*)
    implicit def setDecoder[A](implicit decode: JsonDecoder[A]): JsonDecoder[Set[A]] =
      JsonDecoder(_.asInstanceOf[js.Array[js.Any]].map(decode(_).get).toSet)


    implicit val decodeString: JsonDecoder[String] = JsonDecoder(_.asInstanceOf[String])
    implicit val encodeString: JsonEncoder[String] = JsonEncoder(identity[String])

    implicit val decodeDouble: JsonDecoder[Double] = JsonDecoder(_.asInstanceOf[Double])
    implicit val encodeDouble: JsonEncoder[Double] = JsonEncoder(identity[Double])

    implicit val decodeBoolean: JsonDecoder[Boolean] = JsonDecoder(_.asInstanceOf[Boolean])
    implicit val encodeBoolean: JsonEncoder[Boolean] = JsonEncoder(identity[Boolean])

    implicit val decodeDirection: JsonDecoder[Direction] = JsonDecoder {
      decodeJson[String](_).get match {
        case "Left" => Direction.Left
        case "Right" => Direction.Right
      }
    }
    implicit val encodeDirection: JsonEncoder[Direction] = JsonEncoder {
      case Direction.Left => "Left"
      case Direction.Right => "Right"
    }

    implicit val decodePoint: JsonDecoder[Point] = JsonDecoder {
      decodeJson[Seq[Double]](_).get match {
        case Seq(x, y) => Point(x, y)
      }
    }
    implicit val encodePoint: JsonEncoder[Point] = JsonEncoder {
      case Point(x, y) => encodeJson(Seq(x, y))
    }

    implicit val decodePlayerAction: JsonDecoder[PlayerAction] = JsonDecoder { json =>
      PlayerAction(decodeJson[String](json.player).get, decodeJson[Option[Direction]](json.steeringDirection).get)
    }
    implicit val encodePlayerAction: JsonEncoder[PlayerAction] = JsonEncoder {
      case PlayerAction(player, steeringDirection) =>
        Dynamic.literal(
          player = encodeJson(player),
          steeringDirection = encodeJson(steeringDirection))
    }

    implicit val decodePlayer: JsonDecoder[Player] = JsonDecoder { json =>
      Player(
        decodeJson[String](json.name).get,
        decodeJson[List[Point]](json.body).get,
        decodeJson[Double](json.orientation).get,
        decodeJson[Option[Direction]](json.steeringDirection).get,
        decodeJson[Boolean](json.hasCollided).get)
    }
    implicit val encodePlayer: JsonEncoder[Player] = JsonEncoder {
      case Player(name, body, orientation, steeringDirection, hasCollided) =>
        Dynamic.literal(
          name = encodeJson(name),
          body = encodeJson(body),
          orientation = encodeJson(orientation),
          steeringDirection = encodeJson(steeringDirection),
          hasCollided = encodeJson(hasCollided))
    }

    implicit val decodeStatus: JsonDecoder[Status] = JsonDecoder { outerJson =>
      val decodeRunning = JsonDecoder(decodeJson[String](_).get match { case "Running" => Game.Running })
      val decodeFinished = JsonDecoder(json => Game.Finished(decodeJson[Seq[String]](json.winningOrder).get))
      (decodeFinished(outerJson) orElse decodeRunning(outerJson)).get
    }
    implicit val encodeStatus: JsonEncoder[Status] = JsonEncoder {
      case Game.Running => "Running"
      case Game.Finished(winningOrder) => Dynamic.literal(winningOrder = encodeJson(winningOrder))
    }

    implicit val decodeGameState: JsonDecoder[GameState] = JsonDecoder { json =>
      GameState(decodeJson[Set[Player]](json.players).get, decodeJson[Game.Status](json.state).get)
    }
    implicit val encodeGameState: JsonEncoder[GameState] = JsonEncoder {
      case GameState(players, state) => Dynamic.literal(players = encodeJson(players), state = encodeJson(state))
    }

    implicit val decodeRemoteJoin: JsonDecoder[RemoteJoin] = JsonDecoder { json =>
      RemoteJoin(decodeJson[String](json.peerId).get, decodeJson[Set[String]](json.users).get)
    }
    implicit val encodeRemoteJoin: JsonEncoder[RemoteJoin] = JsonEncoder {
      case RemoteJoin(peerId, users) => Dynamic.literal(peerId = encodeJson(peerId), users = encodeJson(users))
    }
  }
}
