package com.tehasdf.discord.messages

import java.math.BigInteger
import java.time.Instant

import spray.json.JsObject

import com.tehasdf.discord.model._

case class ServerMsg(`type`: String, seq: Int, opCode: Int, data: JsObject)

case class ReadyPayload(session_id: String, heartbeat_interval: Int, guilds: Seq[Guild], v: Int, read_state: Seq[String], private_channels: Seq[String], user: Self)
case class PresenceUpdatePayload(user: Id, status: String, roles: Seq[BigInteger], guildId: BigInteger, gameId: Option[Long])
case class MessageCreatePayload(tts: Boolean, timestamp: Instant, nonce: BigInteger, mentions: Seq[BigInteger], mentionEveryone: Boolean, id: BigInteger, embeds: Seq[String], editedTimestamp: Option[Instant], content: String, channelId: BigInteger, author: User, attachments: Seq[String])