package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.cluster.sharding.ShardRegion


class Prefix extends PersistentActor {
  import Prefix.Protocol._

  override def persistenceId = "Prefix" + self.path.name

  override def receiveRecover: Receive = {
    var contacts: List[(UUID[Contact], String, ActorRef)] = List();
    {
        case RecoveryCompleted => context.become(ready(contacts))
        case evt =>
    }
  }

  override def receiveCommand: Receive = {
    case Search => ""
  }

  private def ready(contacts: List[(UUID[Contact], String, ActorRef)] = List()): Receive = {
    case Search(_) => sender() ! contacts
  }
}


object Prefix {
  import Protocol._

  def shardName = "Index"

  def idExtractor: ShardRegion.ExtractEntityId = {
    case msg @ Search(prefix) => (prefix, msg)
  }

  def shardResolver: ShardRegion.ExtractShardId = {
    case Search(prefix)   => (math.abs(prefix.hashCode) % 100).toString
  }

  def props() = Props(classOf[Prefix])

  object Protocol {
    sealed trait Command extends Product with Serializable {
      def prefix: String
    }

    final case class Search(prefix: String) extends Command
    final case class Add(
        prefix: String,
        id: UUID[Contact],
        name: String,
        manager: ActorRef) extends Command
    final case class Remove(
        prefix: String,
        id: UUID[Contact]) extends Command
  }
}
