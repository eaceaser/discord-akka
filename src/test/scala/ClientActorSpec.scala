package com.tehasdf.discord

import akka.actor.{ActorRef, Actor, ActorSystem}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.ask
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{SourceQueue, ActorMaterializer, OverflowStrategy}
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.concurrent.{IntegrationPatience, Eventually}

import scala.collection.immutable
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import com.tehasdf.discord.ClientActor.ConnectionState
import org.scalatest._

class ClientActorSpec(_sys: ActorSystem) extends TestKit(_sys)
  with FlatSpecLike with BeforeAndAfterAll with ShouldMatchers with Eventually {
  implicit val materializer = ActorMaterializer()
  implicit val iTimeout = Timeout(5 seconds)
  val defaultTimeout = 5 seconds

  def this() = this(ActorSystem("discord-akka-test"))

  import system.dispatcher

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Discord Client" should "handle a standard payload" in {
    withTrace("/traces/basic.txt") { (queue, client, rest) =>
      rest foreach queue.offer

      val expected = Set("khionu", "ceezy")
      val msg1 = expectMsgAnyClassOf(classOf[Api.Message])
      expected should contain(msg1.user)
      val msg2 = expectMsgAnyClassOf(classOf[Api.Message])
      expected should contain(msg2.user)

      val stateF = (client ? Api.GetState).mapTo[ConnectionState]
      val state = Await.result(stateF, defaultTimeout)
      state.guilds.values.head.presences(BigInt(91377040883744768L).underlying)._2.status shouldBe "idle"
    }
  }

  it should "handle a guild add message" in {
    withTrace("/traces/add_membership.txt") { (queue, client, rest) =>
      val stateF = (client ? Api.GetState).mapTo[ConnectionState]
      val state = Await.result(stateF, defaultTimeout)
      state.guilds.values.head.members.get(BigInt(71671589866831872L).underlying) shouldBe None

      Await.result(Future.sequence(rest map queue.offer), defaultTimeout)
      eventually {
        val state2F = (client ? Api.GetState).mapTo[ConnectionState]
        val state2 = Await.result(state2F, defaultTimeout)
        val membership = state2.guilds.values.head.members(BigInt(71671589866831872L).underlying)
        membership.user.username shouldBe "FMendonca"
      }
    }
  }

  private def withTrace(filename: String)(f: (SourceQueue[Message], ActorRef, Iterable[Message]) => Unit) = {
    val dummyQueue = Source.queue[Message](64, OverflowStrategy.fail).to(Sink.ignore).run()
    val clientProps = ClientActor.props(dummyQueue, testActor, "test")
    val sink = Sink.actorSubscriber(clientProps)
    val src = Source.queue[Message](64, OverflowStrategy.fail)
    val (queue, client) = src.toMat(sink)(Keep.both).run()
    val messages = readTrace(filename)
    val (init, rest) = (messages.head, messages.tail)

    queue.offer(init)
    expectMsg(Api.Ready)

    f(queue, client, rest)
  }

  private def readTrace(filename: String): immutable.Iterable[Message] = {
    immutable.Seq(io.Source.fromInputStream(getClass.getResourceAsStream(filename)).getLines().map(TextMessage(_)).toSeq: _*)
  }
}
