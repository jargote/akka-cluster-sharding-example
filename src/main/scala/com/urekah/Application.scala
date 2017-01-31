package com.urekah

import services.{Bot, Directory, Prefix, ContactManager}

import akka.actor.{ActorPath, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}

import com.typesafe.config.ConfigFactory

object Application {
  def main(args: Array[String]): Unit = {
    startup(Seq("2551", "2552", "0"))
  }

  private def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory
        .parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)

      val index = ClusterSharding(system).start(
        typeName = Prefix.shardName,
        entityProps = Prefix.props,
        settings = ClusterShardingSettings(system),
        extractEntityId = Prefix.idExtractor,
        extractShardId = Prefix.shardResolver)
      val contacts = ClusterSharding(system).start(
        typeName = ContactManager.shardName,
        entityProps = ContactManager.props(index),
        settings = ClusterShardingSettings(system),
        extractEntityId = ContactManager.idExtractor,
        extractShardId = ContactManager.shardResolver)

      if (port != "2551" && port != "2552")
        system.actorOf(Props(new Bot(index, contacts)), "bot")
    }
  }
}
