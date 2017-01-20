package com.urekah.services

import com.urekah.models.Contact
import com.urekah.utils.UUID

import akka.actor.{ActorRef, Props, PoisonPill, ReceiveTimeout}
import akka.persistence._
import akka.cluster.sharding.ShardRegion

import scala.collection.immutable.Map
import scala.concurrent.duration._

class Prefix extends PersistentActor {
  import Prefix.Protocol._

  context.setReceiveTimeout(30.seconds)

  override def persistenceId: String = self.path.name

  override def receiveRecover: Receive = {
    var state = Map[UUID[Contact], (String, ActorRef)]();
    {
      case RecoveryCompleted => context.become(ready(state))
      case SnapshotOffer(_, snapshot: Map[UUID[Contact], (String, ActorRef)]) =>
        state = snapshot
      case cmd: Command => state = updateState(cmd, state)
    }
  }

  override def receiveCommand: Receive = {
    case search: Search =>
      persist(search) { evt =>
        var state = Map.empty[UUID[Contact], (String, ActorRef)];
        context.become(ready(state))
        sender ! state
      }
    case ReceiveTimeout => context.stop(self)
  }

  private def ready(state: Map[UUID[Contact], (String, ActorRef)]): Receive = {
    case cmd @ Search(prefix) =>
      sender ! SearchResult(prefix, cmd, buildResults(state))
    case cmd: Command => persist(cmd) { cmd =>
      val nState = updateState(cmd, state)
      context.become(ready(nState))
      sender ! SearchResult(cmd.prefix, cmd, buildResults(nState))
    }
    case SaveSnapshotSuccess => context.stop(self)
    case ReceiveTimeout => saveSnapshot(state)
  }

  private def updateState(evt: Command, state: Map[UUID[Contact], (String, ActorRef)]) =
    evt match {
      case AddEntry(_, id: UUID[Contact], name, manager) =>
        state + ((id, (name, manager)))
      case RemoveEntry(_, id) => state - id
      case _ => state
    }

  private def buildResults(state: Map[UUID[Contact], (String, ActorRef)]) =
    state.map {
      case (id: UUID[_], (name: String, actor: ActorRef)) =>
        (id, name, actor)
    } toSeq
}


object Prefix {
  import Protocol._

  def shardName = "Index"

  def idExtractor: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.prefix, cmd)
  }

  def shardResolver: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.prefix.hashCode) % 100).toString
  }

  def props = Props[Prefix]

  object Protocol {
    import com.urekah.utils.{Protocol => GenProto}

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
