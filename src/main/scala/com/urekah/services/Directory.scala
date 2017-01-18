package com.urekah
package services

import models.Contact
import utils.Protocol.Command
import utils.UUID

import akka.actor.{Actor, ActorRef, ActorLogging}

class Directory(contacts: ActorRef, prefixes: ActorRef) extends Actor with ActorLogging {
  import Directory.Protocol._
  import ContactManager.Protocol.Get
  import Prefix.Protocol.{Search, SearchResult}


  def receive: Receive = {
    case SearchById(id: UUID[Contact]) => contacts ! Get(id)
    case SearchByPrefix(prefix: String) => prefixes ! Search(prefix)
    case SearchResult(prefix, _, data) =>
      val results = data map {
        case (id, name, contact) => s"${id.value.toString} -> $name"
      }
      context.parent ! results
    case Contact(id, firstName, lastName, phoneNumber) =>
      val msg = Seq(firstName, lastName, phoneNumber)
        .foldLeft(s"${id.value.toString} ->") {
        case (str: String, Some(field: Any)) =>
          str + " " + field.toString
        case (str: String, _) => str
      }
      context.parent ! msg
  }
}

object Directory {

  object Protocol {
    case class SearchById(id: UUID[Contact]) extends Command
    case class SearchByPrefix(prefix: String) extends Command
  }
}
