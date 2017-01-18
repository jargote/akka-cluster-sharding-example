package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, ActorSystem}
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
    import Prefix.Protocol.Search
    import ContactManager.Protocol.{Create, UpdateFirstName}

    val aContactId = UUID.random[Contact]
    val prefix = system.actorOf(Prefix.props)
    val contact = system.actorOf(ContactManager.props(prefix, aContactId))

    "#Setup tests" in {
      // Create new prefix
      prefix ! Search("foo")
      expectMsg(Map.empty)

      contact ! Create(aContactId)
      expectMsg(Contact(id = aContactId))

      contact ! UpdateFirstName(aContactId, firstName = "Foo Bar")
      expectMsg(Contact(id = aContactId, firstName = "Foo Bar".some))

      prefix ! Search("foo")
      expectMsgPF() {
        case msg: Map[_, _] =>
          msg should contain (aContactId)
      }
    }

    "#Search by UUID" in {
      1 shouldBe 0
    }

    // "#AddEntry" in {
    //   prefix ! AddEntry("foo", aContactId, "Foo Bar", aContact)
    //   expectMsgPF() {
    //     case result: Map[_, _] =>
    //       result.keys should contain (aContactId)
    //       result.values should contain (("Foo Bar", aContact))
    //   }
    // }
    //
    // "#RemoveEntry" in {
    //   prefix ! RemoveEntry("foo", aContactId)
    //   expectMsgPF() {
    //     case result: Map[_, _] =>
    //       result.keys shouldNot contain (aContactId)
    //   }
    // }
  }
}
