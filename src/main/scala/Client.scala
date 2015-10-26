package com.tehasdf.discord

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
import spray.json.{DefaultJsonProtocol, _}

object Client {
  case class LoginInfo(email: String, password: String)

  object ClientProtocol extends DefaultJsonProtocol {
    implicit val loginInfoF = jsonFormat2(LoginInfo)
  }

  case class LoginFailure(resp: HttpResponse) extends Exception(resp.toString)
}

class Client(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {
  import Client.ClientProtocol._
  import Client._
  import system.dispatcher

  def acquireToken(email: String, password: String) = {
    val body = HttpEntity(ContentTypes.`application/json`, LoginInfo(email, password).toJson.compactPrint)
    val req = HttpRequest(method = HttpMethods.POST, uri = Endpoints.Login, entity = body)
    Http().singleRequest(req).flatMap { resp =>
      resp.status match {
        case _: StatusCodes.Success =>
          Unmarshal(resp.entity).to[JsObject]
        case _ =>
          throw LoginFailure(resp)
      }
    }.map { obj =>
      obj.fields("token").convertTo[String]
    }
  }

  def lookupGateway(token: String) = {
    val req = HttpRequest(uri = Endpoints.Gateway).withHeaders(RawHeader("Authorization", token))
    Http().singleRequest(req).flatMap { resp =>
      resp.status match {
        case _: StatusCodes.Success =>
          Unmarshal(resp.entity).to[JsObject]
        case _ =>
          throw new RuntimeException(resp.toString)
      }
    }.map { obj =>
      obj.fields("url").convertTo[String]
    }
  }

  def wsConnect(uri: Uri, listener: ActorRef, authToken: String) = {
    val sourceQueue = Source.queue[Message](64, OverflowStrategy.fail)
    val destPublisher = Sink.publisher[Message]

    val flow = Flow.fromSinkAndSourceMat(destPublisher, sourceQueue)(Keep.both)
    val (_, (dest, source)) = Http().singleWebsocketRequest(uri, flow)

    val props = ClientActor.props(source, listener, authToken)
    val actor = system.actorOf(props)
    dest.subscribe(ActorSubscriber(actor))

    actor
  }
}
