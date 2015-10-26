package com.tehasdf.discord

import java.math.BigInteger

import akka.actor.{ActorRef, Cancellable, FSM, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, SourceQueue}
import codec.DiscordProtocol
import com.tehasdf.discord.model._
import com.tehasdf.discord.messages._
import com.tehasdf.discord.state.GuildState
import spray.json._

import monocle.macros.GenLens
import monocle.function.{index, at}
import monocle.std._

import scala.concurrent.duration._

object Api {
  case object Ready
  case object GetState
  case class Message(channel: String, user: String, content: String)
}

object ClientActor {
  def props(queue: SourceQueue[Message], listener: ActorRef, authToken: String) = Props(classOf[ClientActor], queue, listener, authToken)

  sealed trait State
  case object Init extends State
  case object Connected extends State

  sealed trait Data
  case object Uninitialized extends Data

  object ConnectionState {
    object Optics {
      val guilds = GenLens[ConnectionState](_.guilds)
    }
  }

  case class ConnectionState(self: Self, guilds: Map[BigInteger, GuildState], heartbeat: Int, hbCancellable: Cancellable) extends Data {
    val channels = guilds.values.flatMap { _.channels.toSeq }.toMap
  }

  case class GoToConnected(payload: ReadyPayload, heartbeat: Int)
  case object SendHeartbeat

  case class UpdatePresence(seq: Int, p: PresenceUpdatePayload)
}

class ClientActor(client: SourceQueue[Message], listener: ActorRef, token: String) extends FSM[ClientActor.State, ClientActor.Data] with ActorSubscriber {
  implicit val mat = ActorMaterializer()
  val system = context.system

  import Api._
  import ClientActor._
  import DiscordProtocol._
  import context.dispatcher

  startWith(Init, Uninitialized)

  override val requestStrategy = OneByOneRequestStrategy

  // Login
  val payload = Login(token, Map("$os" -> "Linux", "$browser" -> "discord-akka", "$device" -> "discord-akka", "$referrer" -> "", "$referring_domain" -> ""))
  val msg = Msg(OpCodes.Login, payload)
  client.offer(TextMessage(msg.toJson.prettyPrint))

  when(Init) {
    case Event(OnNext(msg: TextMessage), Uninitialized) => {
      msg.textStream.runWith(Sink.fold("")(_ + _)).foreach { payload =>
        val m = payload.parseJson.convertTo[ServerMsg]
        m.`type` match {
          case "READY" =>
            val d = m.data.convertTo[ReadyPayload]
            self ! GoToConnected(d, d.heartbeat_interval)
          case _ =>
            // TODO: Log unhandled message here.
        }
      }
      stay
    }
    case Event(s: GoToConnected, Uninitialized) =>
      val cancellable = system.scheduler.schedule(0 seconds, s.heartbeat seconds, self, SendHeartbeat)

      val guilds = s.payload.guilds.map { g =>
        val members = g.members.map { m => m.user.id -> m }.toMap
        val presences = g.presences.map { p => p.user -> ((0, p)) }.toMap
        val channels = g.channels.map { c => c.id -> c }.toMap
        g.id -> GuildState(g, channels, members, presences)
      }.toMap

      listener ! Ready
      goto(Connected) using ConnectionState(s.payload.user, guilds, s.heartbeat, cancellable)
    case x@_ =>
      // TODO: Log unhandled message here.
      stay
  }

  when(Connected) {
    case Event(SendHeartbeat, s: ConnectionState) =>
      val ts = System.currentTimeMillis()
      client.offer(TextMessage(Msg(OpCodes.Ping, Ping(ts)).toJson.prettyPrint))
      stay

    case Event(OnNext(msg: TextMessage), s: ConnectionState) =>
      msg.textStream.runWith(Sink.fold("")(_ + _)).foreach { payload =>
        val m = payload.parseJson.convertTo[ServerMsg]
        m.`type` match {
          case "PRESENCE_UPDATE" =>
            val d = m.data.convertTo[PresenceUpdatePayload]
            self ! UpdatePresence(m.seq, d)
          case "MESSAGE_CREATE" =>
            val d = m.data.convertTo[MessageCreatePayload]
            val channel = s.channels(d.channelId)
            val msg = Api.Message(channel.name, d.author.username, d.content)
            listener ! msg
          case x@_ =>
            // TODO: Log Unhandled Message Here.
        }
      }
      stay

    case Event(UpdatePresence(newSeq, p), s: ConnectionState) =>
      import ConnectionState.Optics._
      import GuildState.Optics._
      val presenceLens = guilds composeOptional(index(p.guildId)) composeLens(presences) composeLens(at(p.user.id))
      val newPresence = Presence(gameId = p.gameId, status = p.status, user = p.user.id)

      val newState = presenceLens.getOption(s).flatten match {
        case Some((oldSeq, oldPresence)) =>
          if (newSeq > oldSeq) {
            presenceLens.set(Some((newSeq, newPresence)))(s)
          } else {
            s
          }
        case None =>
          presenceLens.set(Some((newSeq, newPresence)))(s)
      }

      stay using newState

    // Public API
    case Event(Api.GetState, s: ConnectionState) =>
      sender ! s
      stay
  }
}
