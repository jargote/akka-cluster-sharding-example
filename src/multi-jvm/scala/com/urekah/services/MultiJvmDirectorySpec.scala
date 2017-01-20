package com.urekah.services

import com.urekah.models.Contact
import com.urekah.services.ContactManager.Protocol._
import com.urekah.services.Prefix.Protocol._
import com.urekah.services.Directory.Protocol._
import com.urekah.utils.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ShardRegion, ClusterShardingSettings, ClusterSharding}
import akka.persistence._
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.{TestKit, ImplicitSender, TestProbe}

import cats.syntax.option._
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class DirectoryClusterSpec extends MultiNodeSpec(DirectoryClusterSpec)
  with STMultiNodeSpec with ImplicitSender {
  import DirectoryClusterSpec._

  def initialParticipants = roles.size

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  def startSharding(): Unit = {
    // Index shard region
    val index = ClusterSharding(system).start(
      typeName = Prefix.shardName,
      entityProps = Prefix.props,
      settings = ClusterShardingSettings(system),
      extractEntityId = Prefix.idExtractor,
      extractShardId = Prefix.shardResolver)
    // Contacts shard region
    ClusterSharding(system).start(
      typeName = ContactManager.shardName,
      entityProps = ContactManager.props(index),
      settings = ClusterShardingSettings(system),
      extractEntityId = ContactManager.idExtractor,
      extractShardId = ContactManager.shardResolver)
  }

  // Helper function for creating Contacts
  def createContact(contact: Contact) = {
    val index = ClusterSharding(system).shardRegion(Prefix.shardName)
    val contacts = ClusterSharding(system).shardRegion(ContactManager.shardName)

    // Inserting contact
    contacts ! Create(contact.id)
    expectMsg(Contact(contact.id))

    // Updating first name
    contacts ! UpdateFirstName(contact.id, contact.firstName.get)
    expectMsg(Contact(contact.id, contact.firstName))

    // Updating last name
    contacts ! UpdateLastName(contact.id, contact.lastName.get)
    expectMsg(Contact(contact.id, contact.firstName, contact.lastName))

    // Updating phone number
    contacts ! UpdatePhoneNumber(contact.id, contact.phoneNumber.get)
    expectMsg(Contact(
      contact.id, contact.firstName, contact.lastName, contact.phoneNumber))
  }

  "Directory Cluster Nodes" must {
    val a = UUID.random[Contact]
    "Join cluster" in within(5.seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      enterBarrier("after-cluster-joining")
    }

    "Create one contact from each" in within(5.seconds) {
      runOn(node1) {
        createContact(contactA)
      }

      runOn(node2) {
        createContact(contactB)
      }

      runOn(node3) {
        createContact(contactC)
      }

      enterBarrier("after-contact-creation")
    }

    "Find every created contact" in within(5.seconds) {
      runOn(node1, node2, node3) {
        val probe = TestProbe()
        val index = ClusterSharding(system).shardRegion(Prefix.shardName)
        val contacts = ClusterSharding(system).shardRegion(ContactManager.shardName)
        val directory = system.actorOf(Props(
          new Actor {
            val child = context.actorOf(Props(new Directory(index, contacts)))
            def receive = {
              case msg if sender == child => probe.ref forward msg
              case msg => child forward msg
            }
          })
        )

        awaitAssert {
          within(1.seconds) {
            probe.send(directory, SearchById(contactA.id))
            probe.expectMsg(contactA)
          }
        }

        awaitAssert {
          within(1.seconds) {
            probe.send(directory, SearchById(contactB.id))
            probe.expectMsg(contactB)
          }
        }

        awaitAssert {
          within(1.seconds) {
            probe.send(directory, SearchById(contactC.id))
            probe.expectMsg(contactC)
          }
        }
      }

      enterBarrier("after-contact-search")
    }
  }
}

object DirectoryClusterSpec extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  // Test Contacts
  val contactA = Contact(
    id = UUID.parse[Contact]("3facc4d8-26b2-4903-8029-0923a9396919").get,
    firstName = "FooBaz".some,
    lastName = "BarMan".some,
    phoneNumber = "0745123456".some)
  val contactB = Contact(
    id = UUID.parse[Contact]("3f9f4721-5d5c-4ff5-8f96-d5276c8634a8").get,
    firstName = "BazFoo".some,
    lastName = "BarBaz".some,
    phoneNumber = "0855123456".some)
  val contactC = Contact(
    id = UUID.parse[Contact]("5811591d-1d71-4e95-8021-034213664ae6").get,
    firstName = "FooBar".some,
    lastName = "ManFoo".some,
    phoneNumber = "0745123456".some)

  commonConfig(ConfigFactory.load("application.conf"))
}

// ClusterNodes
class DirectoryClusterSpecMultiJvmNode1 extends DirectoryClusterSpec
class DirectoryClusterSpecMultiJvmNode2 extends DirectoryClusterSpec
class DirectoryClusterSpecMultiJvmNode3 extends DirectoryClusterSpec
