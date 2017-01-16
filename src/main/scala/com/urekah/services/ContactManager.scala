package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor._
import akka.persistence._
import akka.cluster.sharding.ShardRegion.Passivate

import cats.syntax.option._

class ContactManager(id: UUID[Contact]) extends PersistentActor {
  import ContactManager.Protocol._

  override def persistenceId: String = id.value.toString

  override def receiveRecover: Receive = {
    var state: Option[Contact] = None;
    {
      case RecoveryCompleted =>
        state.map(s => context.become(ready(s)))
      case Create(id: UUID[Contact]) =>
        val nState = Contact(id = id)
        state = nState.some
      case evt => state.map(s => updateState(evt, s))
    }
  }

  override def receiveCommand: Receive = {
    case create: Create =>
      persist(create) { evt =>
        val state = Contact(id = create.id)
        context.become(ready(state))
        sender ! state
      }
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = PoisonPill)
  }

  private def ready(state: Contact): Receive = {
    case Get(_) =>
      println("GETTING!!")
      sender ! state
    case firstNameUpdate: UpdateFirstName =>
      persist(firstNameUpdate) { evt =>
        val nState = updateState(evt, state)
        context.become(ready(updateState(evt, nState)))
        sender ! nState
      }
    case lastNameUpdate: UpdateLastName =>
      persist(lastNameUpdate) { evt =>
        val nState = updateState(evt, state)
        context.become(ready(updateState(evt, nState)))
        sender() ! nState
      }
    case phoneNumberUpdate: UpdatePhoneNumber =>
      persist(phoneNumberUpdate) { evt =>
        val nState = updateState(evt, state)
        context.become(ready(updateState(evt, nState)))
        sender ! nState
      }
    case ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = PoisonPill)
  }

  private def updateState(evt: Any, state: Contact): Contact =
    evt match {
      case UpdateFirstName(_, firstName: String) =>
        state.copy(firstName = firstName.some)
      case UpdateLastName(_, lastName: String) =>
        state.copy(lastName = lastName.some)
      case UpdatePhoneNumber(_, phoneNumber: String) =>
        state.copy(phoneNumber = phoneNumber.some)
    }
}

object ContactManager {

  def props(id: UUID[Contact]) = Props(new ContactManager(id))

  object Protocol {

    sealed trait Command
        extends Product
        with Serializable
        with Contact.Audit

    final case class Create(
        id: UUID[Contact]) extends Command
    final case class Get(
        id: UUID[Contact]) extends Command
    final case class UpdateFirstName(
        id: UUID[Contact],
        firstName: String) extends Command
    final case class UpdateLastName(
        id: UUID[Contact],
        lastName: String) extends Command
    final case class UpdatePhoneNumber(
        id: UUID[Contact],
        phoneNumber: String) extends Command
  }
}
