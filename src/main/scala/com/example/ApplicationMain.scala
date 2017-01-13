package com.example

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory
import Counter.{extractShardId, extractEntityId}

object ApplicationMain extends App {
  Seq("2551", "2552", "0") foreach { port =>
    // Override the configuration of the port
    val config = ConfigFactory
      .parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("ClusterSystem", config)

    ClusterSharding(system).start(
      typeName = "Counter",
      entityProps = Props[Counter],
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)
  }
}
