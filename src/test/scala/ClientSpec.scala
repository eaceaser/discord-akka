package com.tehasdf.discord

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, StatusCodes, HttpResponse, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.testkit.TestKit
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class ClientSpec(_sys: ActorSystem) extends TestKit(_sys) with FlatSpecLike with BeforeAndAfterAll with ShouldMatchers {
  def this() = this(ActorSystem("discord-akka-test"))

  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val token = "test test test"

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "Discord Client" should "Send a message" in {
    val testFlow = Flow[(HttpRequest, Int)].map { case (req, i) =>
      val resp = HttpResponse(StatusCodes.OK).withEntity(ContentTypes.`application/json`, "{\"nonce\": \"8778169208853757952\", \"attachments\": [], \"tts\": false, \"embeds\": [], \"timestamp\": \"2015-10-28T22:43:25.802000+00:00\", \"mention_everyone\": false, \"id\": \"109059478082584576\", \"edited_timestamp\": null, \"author\": {\"username\": \"ceezy\", \"discriminator\": \"7914\", \"id\": \"68061741975601152\", \"avatar\": \"1530a9f917209248e49d9dccb8f85049\"}, \"content\": \"test\", \"channel_id\": \"81402706320699392\", \"mentions\": []}")
      (Success(resp), i)
    }
    val client = new AuthenticatedClient(token, testFlow)

    val resultF = client.sendMessage(BigInt(81402706320699392L).underlying, "test", Nil)
    Await.result(resultF, 5 seconds) shouldBe true
  }
}
