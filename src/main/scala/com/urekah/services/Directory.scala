package com.urekah.services

import com.urekah.models.Contact
import com.urekah.utils.Protocol.Command
import com.urekah.utils.UUID

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.cluster.sharding.ClusterSharding

class Directory(index: ActorRef, contacts: ActorRef) extends Actor with ActorLogging {
  import Directory.Protocol._
  import ContactManager.Protocol.Get
  import Prefix.Protocol.{Search, SearchResult}

  def receive: Receive = {
    case SearchById(id) => contacts ! Get(id)
    case SearchByPrefix(prefix: String) => index ! Search(prefix)
    case SearchResult(prefix, _, data) =>
      context.parent ! data.map {
        case (id: UUID[Contact], name: String, contact: ActorRef) =>
          s"$prefix -> ${id.value.toString} -> $name"
      }
    case contact: Contact => context.parent ! contact
  }
}

object Directory {

  object Protocol {
    case class SearchById(id: UUID[Contact]) extends Command
    case class SearchByPrefix(prefix: String) extends Command
  }
}
