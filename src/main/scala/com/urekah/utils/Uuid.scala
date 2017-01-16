package com.urekah
package utils

import akka.http.scaladsl.unmarshalling.Unmarshaller

import java.{util => ju}
import scala.util.Try
import cats.Eq

final case class UUID[A](value: ju.UUID) extends AnyVal

object UUID {

  val RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  def random[A]: UUID[A] = UUID[A](ju.UUID.randomUUID)

  def parse[A](str: String): Option[UUID[A]] =
    Option(str).flatMap(s =>
        Try {
        UUID[A](ju.UUID.fromString(s))
      }.toOption)

  def deserializer[A] = Unmarshaller.strict[String, Option[UUID[A]]](UUID.parse[A])

  def requiredDeserializer[A] =
    Unmarshaller.strict[String, UUID[A]](id =>
        UUID.parse[A](id).getOrElse(throw new IllegalArgumentException))

  implicit def UUIDEq[A]: Eq[UUID[A]] =
    new Eq[UUID[A]] {
      override def eqv(x: UUID[A], y: UUID[A]): Boolean =
        x.value equals y.value
    }

}
