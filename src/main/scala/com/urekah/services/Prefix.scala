package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, Props, PoisonPill, ReceiveTimeout}
import akka.persistence._
import akka.cluster.sharding.ShardRegion
import scala.collection.immutable.Map


class Prefix extends PersistentActor {
  import Prefix.Protocol._

  override def persistenceId: String = "Prefix" + self.path.name

  override def receiveRecover: Receive = {
    var state = Map[UUID[Contact], (String, ActorRef)]();
    {
        case RecoveryCompleted => context.become(ready(state))
        case SnapshotOffer(_, snapshot: Map[UUID[Contact], (String, ActorRef)]) =>
          state = snapshot
        case evt: Command => state = updateState(evt, state)
    }
  }

  override def receiveCommand: Receive = {
    case search: Search =>
      persist(search) { evt =>
        var state = Map.empty[UUID[Contact], (String, ActorRef)];
        context.become(ready(state))
        sender ! state
      }
    case ReceiveTimeout =>
      context.parent ! ShardRegion.Passivate(stopMessage = PoisonPill)
  }

  private def ready(state: Map[UUID[Contact], (String, ActorRef)]): Receive = {
    case Search(_) => sender() ! state
    case evt: Command => persist(evt) { evt =>
      val nState = updateState(evt, state)
      context.become(ready(nState))
      sender ! nState
    }
    case SaveSnapshotSuccess =>
      context.parent ! ShardRegion.Passivate(stopMessage = PoisonPill)
    case ReceiveTimeout =>
      saveSnapshot(state)
  }

  private def updateState(evt: Command, state: Map[UUID[Contact], (String, ActorRef)]) =
    evt match {
      case AddEntry(_, id: UUID[Contact], name, manager) =>
        state + ((id, (name, manager)))
      case RemoveEntry(_, id) => state - id
      case _ => state
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

  def props = Props[Prefix]

  object Protocol {
    import utils.{Protocol => GenProto}

    sealed trait Command extends GenProto.Command {
      def prefix: String
    }

    final case class Search(prefix: String) extends Command
    final case class AddEntry(
        prefix: String,
        id: UUID[Contact],
        name: String,
        manager: ActorRef) extends Command
    final case class RemoveEntry(
        prefix: String,
        id: UUID[Contact]) extends Command
    final case class SearchResult(
      prefix: String,
      cmd: Command,
      data: Seq[(UUID[Contact], String, ActorRef)]) extends Command
        with GenProto.Result[Seq[(UUID[Contact], String, ActorRef)]]
  }
}
