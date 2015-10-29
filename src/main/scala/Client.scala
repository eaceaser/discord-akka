package com.tehasdf.discord

import java.math.BigInteger

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.actor.ActorSubscriber
import akka.stream.{OverflowStrategy, ActorMaterializer}
import akka.stream.scaladsl.{Keep, Flow, Sink, Source}
import com.tehasdf.discord.Client.LoginFailure
import com.tehasdf.discord.messages.MessageCreatePayload
import com.tehasdf.discord.model.{Msg, LoginInfo}
import spray.json._

import scala.util.{Success, Failure, Try, Random}

object Client {
  case class LoginFailure(resp: HttpResponse) extends Exception(resp.toString)
}

class Client(http: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Unit])(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {
  import codec.DiscordProtocol._
  import system.dispatcher

  def this()(implicit s: ActorSystem, m: ActorMaterializer) = this(Http().superPool())

  def acquireToken(email: String, password: String) = {
    val body = HttpEntity(ContentTypes.`application/json`, LoginInfo(email, password).toJson.compactPrint)
    val req = HttpRequest(method = HttpMethods.POST, uri = Endpoints.Login, entity = body)

    Source.single(req -> 1).via(http).runWith(Sink.head).map(_._1).flatMap {
      case Success(resp) =>
        resp.status match {
          case _: StatusCodes.Success =>
            Unmarshal(resp.entity).to[JsObject]
          case _ =>
            throw LoginFailure(resp)
        }
      case Failure(ex) =>
        throw ex
    }.map { obj =>
      obj.fields("token").convertTo[String]
    }
  }

  def authenticate(email: String, password: String) = {
    acquireToken(email, password).map { new AuthenticatedClient(_) }
  }
}

class AuthenticatedClient(token: String, http: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Unit])(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {
  import system.dispatcher
  import codec.DiscordProtocol._

  def this(token: String)(implicit s: ActorSystem, m: ActorMaterializer) = this(token, Http().superPool())

  private val authHeader = RawHeader("Authorization", token)

  def lookupGateway() = {
    val req = HttpRequest(uri = Endpoints.Gateway).withHeaders(authHeader)
    Source.single(req -> 1).via(http).runWith(Sink.head).map(_._1).flatMap {
      case Success(resp) =>
        resp.status match {
          case _: StatusCodes.Success =>
            Unmarshal(resp.entity).to[JsObject]
          case _ =>
            throw new RuntimeException(resp.toString)
        }
      case Failure(ex) =>
        throw ex
    }.map { obj =>
      obj.fields("url").convertTo[String]
    }
  }

  def sendMessage(channelId: BigInteger, message: String, mentions: Seq[BigInteger]) = {
    val uri = Endpoints.Channel + "/" + channelId + "/messages"
    val nonce = BigInt(Random.nextLong)
    val body = HttpEntity(ContentTypes.`application/json`, Msg(message, mentions, nonce.underlying, false).toJson.compactPrint)
    val req = HttpRequest(method = HttpMethods.POST, uri = uri, entity = body).withHeaders(authHeader)
    Source.single(req -> 1).via(http).runWith(Sink.head).map(_._1).flatMap {
      case Success(resp) =>
        resp.status match {
          case _: StatusCodes.Success =>
            Unmarshal(resp.entity).to[JsObject]
          case _ =>
            throw new RuntimeException(resp.toString)
        }
      case Failure(ex) =>
        throw ex
    }.map { obj =>
      val payload = obj.convertTo[MessageCreatePayload]
      true // TODO: probably return the payload as a resource of some sort.
    }
  }

  def wsConnect(uri: Uri, listener: ActorRef) = {
    val sourceQueue = Source.queue[Message](64, OverflowStrategy.fail)
    val destPublisher = Sink.publisher[Message]

    val flow = Flow.fromSinkAndSourceMat(destPublisher, sourceQueue)(Keep.both)
    val (_, (dest, source)) = Http().singleWebsocketRequest(uri, flow)

    val props = ClientActor.props(source, listener, token)
    val actor = system.actorOf(props)
    dest.subscribe(ActorSubscriber(actor))

    actor
  }
}
