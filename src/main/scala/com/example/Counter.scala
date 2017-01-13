package com.example

import akka.actor._
import akka.persistence._
import akka.cluster.sharding.ShardRegion
import akka.util.HashCode

import scala.concurrent.duration._

class Counter(id: String) extends PersistentActor {
  import Counter._
  import ShardRegion.Passivate

  context.setReceiveTimeout(120.seconds)

  // self.path.name is the entity identifier (utf-8 URL-encoded)
  override def persistenceId: String = "CounterPersID-" + self.path.name

  var count = 0

  def updateState(event: CounterChanged): Unit =
    count += event.delta

  override def receiveRecover: Receive = {
    case evt: CounterChanged => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case Increment(_)   => persist(CounterChanged(+1))(updateState)
    case Decrement(_)   => persist(CounterChanged(-1))(updateState)
    case Get(_)         => sender() ! count
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop(_)        => context.stop(self)
  }
}

object Counter {
  val numberOfShards = 10
  val shardName = "Counter"

  sealed trait Command {
    def id: String
  }

  final case class Increment(id: String) extends Command
  final case class Decrement(id: String) extends Command
  final case class Get(id: String) extends Command
  final case class Stop(id: String) extends Command
  final case class CounterChanged(delta: Int)

  def props(id: String): Props = Props(new Counter(id))

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (
      HashCode.hash(HashCode.SEED, cmd.id).toString, cmd)
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (
      HashCode.hash(HashCode.SEED, cmd.id) % numberOfShards).toString
  }
}
