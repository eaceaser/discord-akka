package com.tehasdf.discord.messages

case class Msg[A](op: Int, d: A)

sealed trait Payload
case class Ping(ts: Long)
case class Login(token: String, properties: Map[String, String], v: Int = 2) extends Payload
