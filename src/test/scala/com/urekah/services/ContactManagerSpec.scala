package com.urekah
package services


import models.Contact
import utils.UUID

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.persistence._
import akka.testkit.{TestKit, ImplicitSender, TestProbe}

import cats.syntax.option._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class ContactManagerSpec
    extends TestKit(ActorSystem(classOf[ContactSpec].getSimpleName))
    with ImplicitSender
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ContactManager companion" must {
    "generate list of prefixes" in {
      ContactManager.buildPrefixes("MyTestMcTestyFace") shouldEqual Set(
        "m", "my", "myt", "myte", "mytes", "mytest", "mytestm",
        "mytestmc", "mytestmct", "mytestmcte", "mytestmctes", "mytestmctest",
        "mytestmctesty", "mytestmctestyf", "mytestmctestyfa",
        "mytestmctestyfac", "mytestmctestyface")
    }
  }

  "ContactManager Service" must {
    import ContactManager.Protocol._
    import Prefix.Protocol._

    val index = TestProbe("Index")
    val anId = UUID.random[Contact]
    val contactMan = system.actorOf(ContactManager.props(index.ref, anId))

    def verifyPrefixes(newValue: String, name: String) = {
      index.expectMsgAllOf(100 millis,
        ContactManager.buildPrefixes(newValue).map { prefix =>
          AddEntry(prefix, anId, name, contactMan)
        } toSeq:_*)
    }

    "#Create a Contact" in {
      contactMan ! Create(anId)
      expectMsg(Contact(id = anId))
    }

    "#Get a Contact" in {
      contactMan ! Get(anId)
      expectMsg(Contact(id = anId))
    }

    "#Update first name" in {
      val newValue = "FooBaz"
      contactMan ! UpdateFirstName(anId, firstName = newValue)
      expectMsg(Contact(id = anId, firstName = newValue.some))
      verifyPrefixes(newValue, "FooBaz")
    }

    "#Update last name" in {
      val newValue = "BarMan"
      contactMan ! UpdateLastName(anId, lastName = newValue)
      expectMsg(Contact(
          id = anId, firstName = "FooBaz".some, lastName = newValue.some))
      verifyPrefixes(newValue, "FooBazBarMan")
    }

    "#Update phone number" in {
      val newValue = "+0000000"
      contactMan ! UpdatePhoneNumber(anId, phoneNumber = newValue)
      expectMsg(Contact(
          id = anId, firstName = "FooBaz".some, lastName = "BarMan".some,
          phoneNumber = newValue.some))
      verifyPrefixes(newValue, "FooBazBarMan")
    }

    "remove an obsolete prefix" in {
      val newValue = "B"
      contactMan ! UpdateFirstName(anId, firstName = newValue)
      expectMsg(Contact(
          id = anId, firstName = newValue.some, lastName = "BarMan".some,
          phoneNumber = "+0000000".some))
      index.expectMsgAllOf(100 millis,
        (ContactManager.buildPrefixes("FooBaz").map { prefix =>
          RemoveEntry(prefix, anId)
        } toSeq) ++
        (ContactManager.buildPrefixes("B").map { prefix =>
          AddEntry(prefix, anId, "BBarMan", contactMan)
        } toSeq):_*)
    }
  }
}
