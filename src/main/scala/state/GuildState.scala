package com.tehasdf.discord.state

import java.math.BigInteger

import com.tehasdf.discord.model._
import monocle.macros.GenLens

object GuildState {
  object Optics {
    val presences = GenLens[GuildState](_.presences)
    val members = GenLens[GuildState](_.members)
  }
}

case class GuildState(guild: Guild,
                      channels: Map[BigInteger, Channel],
                      members: Map[BigInteger, Membership],
                      presences: Map[BigInteger, (Int, Presence)]) {}
