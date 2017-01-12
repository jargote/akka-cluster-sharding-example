package com.example

import akka.actor.{ActorSystem, Actor, Props, ActorRef}
import akka.cluster.sharding.{ShardRegion, ClusterShardingSettings, ClusterSharding}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ClusterShardingSpec() extends TestKit(ActorSystem("test")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ClusterSharding#Prototype" must {
    val numberOfShards = 20
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case EntityEnvelope(id, payload) ⇒ (id.toString, payload)
      case msg @ Get(id)               ⇒ (id.toString, msg)
    }
    val extractShardId: ShardRegion.ExtractShardId = {
      case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
      case Get(id)               ⇒ (id % numberOfShards).toString
    }
    val counterRegion: ActorRef = ClusterSharding(system).start(
      typeName = "Counter",
      entityProps = Props[Counter],
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)

    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }
  }
}
