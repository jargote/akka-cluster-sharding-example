package com.urekah
package models

import utils.UUID

final case class Contact(
    id: UUID[Contact],
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    phoneNumber: Option[String] = None) {

}

object Contact {
  trait Audit {
    def id: UUID[Contact]
  }

  def create = Contact(id = UUID.random[Contact])
}
