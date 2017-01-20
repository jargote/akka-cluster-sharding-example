package com.urekah.models

import com.urekah.utils.UUID

final case class Contact(
    id: UUID[Contact],
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    phoneNumber: Option[String] = None) {

  def fullname = {
    Seq(firstName, lastName).foldLeft("") {
      case (str: String, Some(field: Any)) =>
        str + field.toString + " "
      case (str: String, _) => str
    }
  } trim

  override def toString = {
    Seq(firstName, lastName, phoneNumber).foldLeft(s"${id.value.toString} ->") {
      case (str: String, Some(field: Any)) =>
        str + " " + field.toString
      case (str: String, _) => str
    }
  } trim

}

object Contact {
  trait Audit {
    def id: UUID[Contact]
  }

  def create = Contact(id = UUID.random[Contact])
}
