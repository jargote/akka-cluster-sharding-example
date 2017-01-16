package com.example

import akka.actor.{ActorSystem, Actor, Props, ActorRef}
import akka.cluster.Cluster
import akka.cluster.sharding.{ShardRegion, ClusterShardingSettings, ClusterSharding}
import akka.testkit.{TestActors, TestKit, ImplicitSender}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.typesafe.config.ConfigFactory
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}
import scala.concurrent.duration._

object CounterClusterSpec extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  // val node3 = role("node3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.stdout-loglevel = INFO
    akka.cluster.metrics.enabled=off
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.persistence.journal.plugin = "inmemory-journal"
    akka.persistence.snapshot-store.plugin = "inmemory-snapshot-store"
    """))
}

// ClusterNodes
class CounterClusterSpecMultiJvmNode1 extends CounterClusterSpec
class CounterClusterSpecMultiJvmNode2 extends CounterClusterSpec
// class CounterClusterSpecMultiJvmNode3 extends CounterClusterSpec

class CounterClusterSpec extends MultiNodeSpec(CounterClusterSpec)
  with STMultiNodeSpec with ImplicitSender {
  import CounterClusterSpec._

  def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  def startSharding(): Unit = {
    ClusterSharding(system).start(
      typeName = Counter.shardName,
      entityProps = Counter.props("some-id"),
      settings = ClusterShardingSettings(system),
      extractEntityId = Counter.extractEntityId,
      extractShardId = Counter.extractShardId)
  }

  def repeatMsg(times: Int, region: ActorRef, msg: Any) = {
    // Increment Counter 5 times
    0 until times foreach { i =>
      region ! msg
    }
  }

  "Sharded Counter" must {
    "join cluster" in within(15.seconds) {
      join(node1, node1)
      join(node2, node1)
      enterBarrier("after-1")
    }

    "Increment counter from across all nodes" in within(15.seconds) {
      runOn(node1) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        // Increment Counter 5 times
        repeatMsg(5, counterRegion, Counter.Increment("counterA"))

        // Check Counter value
        counterRegion ! Counter.Get("counterA")
        expectMsg(5)
      }

      runOn(node2) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)
        awaitAssert {
          within(1.second) {
            // Check Counter value
            counterRegion ! Counter.Get("counterA")
            expectMsg(5)

            // Increment Counter 5 times
            repeatMsg(5, counterRegion, Counter.Increment("counterA"))
          }
        }
      }

      enterBarrier("after-2")

      runOn(node1) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        awaitAssert {
          within(1.second) {
            counterRegion ! Counter.Get("counterA")
            expectMsg(10)
          }
        }
      }

      runOn(node2) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        awaitAssert {
          within(1.second) {
            counterRegion ! Counter.Get("counterA")
            expectMsg(10)
          }
        }
      }

      enterBarrier("after-3")
    }

    "Should restore entity from a persistent state" in within(15.seconds) {
      runOn(node1) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        // Stopping test counter
        counterRegion ! Counter.Stop("counterA")

        enterBarrier("after-4")
      }

      runOn(node2) {
        enterBarrier("after-4")

        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        counterRegion ! Counter.Increment("counterA")

        enterBarrier("after-5")
      }

      runOn(node1) {
        enterBarrier("after-5")

        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        awaitAssert {
          within(1.second) {
            // Stopping test counter
            counterRegion ! Counter.Get("counterA")
            expectMsg(11)
          }
        }

        enterBarrier("after-6")
      }

      runOn(node2) {
        val counterRegion = ClusterSharding(system).shardRegion(Counter.shardName)

        awaitAssert {
          within(1.second) {
            // Stopping test counter
            counterRegion ! Counter.Get("counterA")
            expectMsg(11)
          }
        }

        enterBarrier("after-6")
      }
    }
  }
}
