package com.tehasdf.discord.codec

import java.math.BigInteger
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.tehasdf.discord.model._
import com.tehasdf.discord.messages._
import spray.json._

object DiscordProtocol extends DefaultJsonProtocol {
  implicit def wsMsgF[A: JsonFormat] = jsonFormat2(WsClientMessage.apply[A])

  implicit val loginF = jsonFormat3(Login)
  implicit object PingFormat extends JsonFormat[Ping] {
    override def read(json: JsValue): Ping = Ping(json.convertTo[Long])
    override def write(obj: Ping): JsValue = JsNumber(obj.ts)
  }

  implicit object InstantFormat extends JsonFormat[Instant] {
    override def read(json: JsValue): Instant = {
      ZonedDateTime.parse(json.convertTo[String]).toInstant
    }

    override def write(obj: Instant): JsValue = JsString(ZonedDateTime.ofInstant(obj, ZoneId.of("UTC")).toString)
  }

  implicit object BigIntegerFormat extends JsonFormat[BigInteger] {
    override def read(json: JsValue): BigInteger = new BigInteger(json.convertTo[String])

    override def write(obj: BigInteger): JsValue = JsString(obj.toString)
  }

  implicit object GuildFormat extends JsonFormat[Guild] {
    override def read(json: JsValue): Guild = {
      val obj = json.asJsObject
      Guild(afkChannelId = obj.fields("afk_channel_id").convertTo[Option[Long]],
        afkTimeout = obj.fields("afk_timeout").convertTo[Long],
        channels = obj.fields("channels").convertTo[Seq[Channel]],
        icon = obj.fields("icon").convertTo[String],
        id = obj.fields("id").convertTo[BigInteger],
        joinedAt = obj.fields("joined_at").convertTo[Instant],
        large = obj.fields("large").convertTo[Boolean],
        members = obj.fields("members").convertTo[Seq[Membership]],
        name = obj.fields("name").convertTo[String],
        ownerId = obj.fields("owner_id").convertTo[BigInteger],
        presences = obj.fields("presences").convertTo[Seq[Presence]],
        region = obj.fields("region").convertTo[String],
        roles = obj.fields("roles").convertTo[Seq[Role]])
    }

    override def write(obj: Guild): JsValue = {
      JsObject(
        "afk_channel_id" -> obj.afkChannelId.toJson,
        "afk_timeout" -> obj.afkTimeout.toJson,
        "channels" -> obj.channels.toJson,
        "icon" -> obj.icon.toJson,
        "id" -> obj.id.toJson,
        "joined_at" -> obj.joinedAt.toJson,
        "large" -> obj.large.toJson,
        "members" -> obj.members.toJson,
        "name" -> obj.name.toJson,
        "owner_id" -> obj.ownerId.toJson,
        "presences" -> obj.presences.toJson,
        "region" -> obj.region.toJson,
        "roles" -> obj.roles.toJson
      )
    }
  }

  implicit object PresenceFormat extends JsonFormat[Presence] {
    override def read(json: JsValue): Presence = {
      val obj = json.asJsObject
      Presence(
        gameId = obj.fields("game_id").convertTo[Option[Long]],
        status = obj.fields("status").convertTo[String],
        user = obj.fields("user").convertTo[Id].id
      )
    }

    override def write(obj: Presence): JsValue = {
      JsObject(
        "game_id" -> obj.gameId.toJson,
        "status" -> obj.status.toJson,
        "user" -> Id(obj.user).toJson
      )
    }
  }

  implicit val serverMsgF = jsonFormat(ServerMsg, "t", "s", "op", "d")

  implicit val selfF = jsonFormat6(Self)
  implicit val userF = jsonFormat4(User)
  implicit val idF = jsonFormat1(Id)
  implicit val membershipF = jsonFormat(Membership, "deaf", "joined_at", "mute", "roles", "user")
  implicit val roleF = jsonFormat7(Role)
  implicit val permissionF = jsonFormat(Permission, "allow", "deny", "id", "type")
  implicit val channelF = jsonFormat(Channel, "id", "last_message_id", "name", "permission_overwrites", "position", "topic", "type")

  implicit val readyF = jsonFormat7(ReadyPayload)
  implicit val presenceUpdateF = jsonFormat(PresenceUpdatePayload, "user", "status", "roles", "guild_id", "game_id")
  implicit val messageCreateF = jsonFormat(MessageCreatePayload, "tts", "timestamp", "nonce", "mentions", "mention_everyone", "id", "embeds", "edited_timestamp", "content", "channel_id", "author", "attachments")

  implicit val guildMemberAddF = jsonFormat(GuildMemberAddPayload, "user", "roles", "joined_at", "guild_id")
  implicit val loginInfoF = jsonFormat2(LoginInfo)
  implicit val msgF = jsonFormat4(Msg)
}
