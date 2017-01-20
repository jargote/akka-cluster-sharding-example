package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, ActorSystem, Actor}
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
    val anId = UUID.random[Contact]
    val dummyActor = TestProbe().ref

    "#Search a Prefix" in {
      val cmd = Search("foo")
      prefix ! cmd
      expectMsg(SearchResult("foo", cmd, Seq()))
    }

    "#AddEntry" in {
      val cmd = AddEntry("foo", anId, "Foo Bar", dummyActor)
      prefix ! cmd
      expectMsg(SearchResult("foo", cmd,
          Seq((anId, "Foo Bar", dummyActor))))
    }

    "#RemoveEntry" in {
      val cmd = RemoveEntry("foo", anId)
      prefix ! cmd
      expectMsg(SearchResult("foo", cmd, Seq()))
    }
  }
}
