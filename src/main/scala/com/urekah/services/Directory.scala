package com.urekah
package services

import models.Contact
import utils.Protocol.Command
import utils.UUID

import akka.actor.{Actor, ActorRef, ActorLogging}

class Directory(contacts: ActorRef, prefixes: ActorRef) extends Actor with ActorLogging {
  import Directory.Protocol._

  def receive: Receive = {
    case SearchByPrefix(prefix: String) => sender ! Unit
    case SearchById(id: UUID[Contact]) => sender ! Unit
  }
}

object Directory {

  object Protocol {
    case class SearchById(id: UUID[Contact]) extends Command
    case class SearchByPrefix(prefix: String) extends Command
  }
}
