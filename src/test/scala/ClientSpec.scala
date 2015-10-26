package com.tehasdf.discord

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.ask
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.testkit.TestKit
import akka.util.Timeout

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._

import com.tehasdf.discord.ClientActor.ConnectionState
import org.scalatest._

class ClientSpec(_sys: ActorSystem) extends TestKit(_sys) with FlatSpecLike with BeforeAndAfterAll with ShouldMatchers {
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5 seconds)

  def this() = this(ActorSystem("discord-akka-test"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Discord Client" should "handle a standard payload" in {
    val dummyQueue = Source.queue[Message](64, OverflowStrategy.fail).to(Sink.ignore).run()
    val clientProps = ClientActor.props(dummyQueue, testActor, "test")
    val sink = Sink.actorSubscriber(clientProps)
    val src = Source.queue[Message](64, OverflowStrategy.fail)
    val (queue, client) = src.toMat(sink)(Keep.both).run()

    val messages = readTrace("/traces/basic.txt")
    val (init, rest) = (messages.head, messages.tail)

    queue.offer(init)
    expectMsg(5 seconds, Api.Ready)
    rest.map { queue.offer(_) }

    val expected = Set("khionu", "ceezy")
    val msg1 = expectMsgAnyClassOf(classOf[Api.Message])
    expected should contain (msg1.user)
    val msg2 = expectMsgAnyClassOf(classOf[Api.Message])
    expected should contain (msg2.user)

    val stateF = (client ? Api.GetState).mapTo[ConnectionState]
    val state = Await.result(stateF, 5 seconds)
    state.guilds.values.head.presences(BigInt(91377040883744768L).underlying)._2.status shouldBe "idle"
  }

  private def readTrace(filename: String): immutable.Iterable[Message] = {
    immutable.Seq(io.Source.fromInputStream(getClass.getResourceAsStream(filename)).getLines().map(TextMessage(_)).toSeq: _*)
  }
}
