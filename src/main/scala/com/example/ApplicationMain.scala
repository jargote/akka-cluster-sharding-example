package com.example

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}


object ApplicationMain extends App {
  val system = ActorSystem("clusterio")
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) ⇒ (id.toString, payload)
    case msg @ Get(id)               ⇒ (id.toString, msg)
  }
  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
    case Get(id)               ⇒ (id % numberOfShards).toString
  }
  val numberOfShards = 20
  val counterRegion: ActorRef = ClusterSharding(system).start(
    typeName = "Counter",
    entityProps = Props[Counter],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)
}
