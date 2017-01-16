package com.urekah
package services


import models.Contact
import utils.UUID

import akka.actor.{ActorSystem, Props}
import akka.persistence._
import akka.testkit.{TestKit, ImplicitSender}

import cats.syntax.option._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class ContactManagerSpec
    extends TestKit(ActorSystem(classOf[ContactSpec].getSimpleName))
    with ImplicitSender
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ContactManager Service" must {
    import ContactManager.Protocol._
    val anId = UUID.random[Contact]
    val contactMan = system.actorOf(ContactManager.props(anId))

    "#Create a Contact" in {
      contactMan ! Create(anId)
      expectMsg(Contact(id = anId))
    }

    "#Get a Contact" in {
      contactMan ! Get(anId)
      expectMsg(Contact(id = anId))
    }

    "#Update first name" in {
      val contactMan = system.actorOf(ContactManager.props(anId))

      contactMan ! UpdateFirstName(anId, firstName = "FooBazBarMan")
      expectMsg(Contact(id = anId, firstName = "FooBazBarMan".some))
    }

    "#Update last name" in {
      val contactMan = system.actorOf(ContactManager.props(anId))

      contactMan ! UpdateLastName(anId, lastName = "BarMan")
      expectMsg(Contact(id = anId, lastName = "BarMan".some))
    }

    "#Update phone number" in {
      val contactMan = system.actorOf(ContactManager.props(anId))

      contactMan ! UpdatePhoneNumber(anId, phoneNumber = "+0000000")
      expectMsg(Contact(id = anId, phoneNumber = "+0000000".some))
    }
  }
}
