package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence._
import akka.testkit.{TestKit, ImplicitSender, TestProbe}

import cats.syntax.option._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PrefixSpec
    extends TestKit(ActorSystem(classOf[PrefixSpec].getSimpleName))
    with ImplicitSender
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Prefix Service" must {
    import Prefix.Protocol._

    val prefix = system.actorOf(Prefix.props)
    val aContactId = UUID.random[Contact]
    val aContact = system.actorOf(ContactManager.props(prefix, aContactId))

    "#Search a Prefix" in {
      prefix ! Search("foo")
      expectMsg(Map.empty)
    }

    "#AddEntry" in {
      prefix ! AddEntry("foo", aContactId, "Foo Bar", aContact)
      expectMsgPF() {
        case result: Map[_, _] =>
          result.keys should contain (aContactId)
          result.values should contain (("Foo Bar", aContact))
      }
    }

    "#RemoveEntry" in {
      prefix ! RemoveEntry("foo", aContactId)
      expectMsgPF() {
        case result: Map[_, _] =>
          result.keys shouldNot contain (aContactId)
      }
    }
  }
}
