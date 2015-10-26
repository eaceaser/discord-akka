package com.tehasdf.discord.model

import java.math.BigInteger
import java.time.Instant

import spray.json.JsValue

import monocle.macros._
import monocle.std._

sealed trait UserLike {
  val avatar: Option[String]
  val username: String
  val discriminator: JsValue
  val id: BigInteger
}

case class User(avatar: Option[String], username: String, discriminator: JsValue, id: BigInteger) extends UserLike
case class Self(avatar: Option[String],
                email: String,
                username: String,
                discriminator: JsValue,
                id: BigInteger,
                verified: Boolean) extends UserLike

case class Permission(
 allow: Int,
 deny: Int,
 id: BigInteger,
 `type`: String
)

case class Channel(
  id: BigInteger,
  lastMessageId: Option[String],
  name: String,
  permissionOverwrites: Seq[Permission],
  position: Int,
  topic: String,
  `type`: String)

case class Id(id: BigInteger)
case class Presence(gameId: Option[Long], status: String, user: BigInteger)
case class Membership(deaf: Boolean, joinedAt: Instant, mute: Boolean, roles: Seq[BigInteger], user: User)

case class Role(color: Int, hoist: Boolean, id: BigInteger, managed: Boolean, name: String, permissions: Int, position: Int)

case class Guild(
  afkChannelId: Option[Long],
  afkTimeout: Long,
  channels: Seq[Channel],
  icon: String,
  id: BigInteger,
  joinedAt: Instant,
  large: Boolean,
  members: Seq[Membership],
  name: String,
  ownerId: BigInteger,
  presences: Seq[Presence],
  region: String,
  roles: Seq[Role]) {
}
