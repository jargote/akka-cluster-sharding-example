package com.urekah
package services

import models.Contact
import utils.UUID

import Directory.Protocol._
import ContactManager.Protocol.Get
import Prefix.Protocol.{Search, SearchResult}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.persistence._
import akka.testkit.{TestKit, ImplicitSender, TestProbe}

import cats.syntax.option._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DirectorySpec
    extends TestKit(ActorSystem(classOf[DirectorySpec].getSimpleName))
    with ImplicitSender
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Directory Service" must {
    val anId = UUID.random[Contact]
    val aContact = Contact(
      id = anId,
      firstName = "FooBaz".some,
      lastName = "BarMan".some,
      phoneNumber = "+0000000".some)

    val probe = TestProbe()
    val mockIndexAndContacts = system.actorOf(Props(new MockActor(aContact)))
    val parent = system.actorOf(Props(new Actor {
      val child = context.actorOf(Props(
        new Directory(mockIndexAndContacts, mockIndexAndContacts)))
      def receive = {
        case msg if sender == child => probe.ref forward msg
        case msg => child forward msg
      }
    }))

    "#SearchById" in {
      probe.send(parent, SearchById(anId))
      probe.expectMsg(anId.value.toString + " -> FooBaz BarMan +0000000")
    }

    "#SearchByPrefix" in {
      probe.send(parent, SearchByPrefix("foo"))
      probe.expectMsg(List(anId.value.toString + " -> FooBazBarMan"))
    }
  }
}

// class
class MockActor(contact: Contact) extends Actor {
  def receive: Receive = {
    case Get(_) =>
      sender ! contact
    case cmd @ Search(prefix) =>
      sender ! SearchResult(prefix, cmd,
          Seq((contact.id, contact.firstName.get + contact.lastName.get, self)))
  }
}
