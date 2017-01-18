package com.urekah
package services

import models.Contact
import utils.UUID

import akka.actor.{ActorRef, Props, ReceiveTimeout, PoisonPill}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.cluster.sharding.ShardRegion

import cats.syntax.option._

class ContactManager(index: ActorRef, id: UUID[Contact]) extends PersistentActor {
  import ContactManager.Protocol._
  import Prefix.Protocol._

  override def persistenceId: String = id.value.toString

  override def receiveRecover: Receive = {
    var state: Option[Contact] = None;
    {
      case RecoveryCompleted =>
        state.map(s => context.become(ready(s)))
      case Create(id: UUID[Contact]) =>
        state = Contact(id = id).some
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
      context.parent ! ShardRegion.Passivate(stopMessage = PoisonPill)
  }

  private def ready(state: Contact): Receive = {
    case Get(_) => sender ! state
    case firstNameUpdate: UpdateFirstName =>
      persist(firstNameUpdate) { evt =>
        val nState = updateState(evt, state)

        // Building prefixes
        val newPrefixes = nState.firstName.map(
          fn => ContactManager.buildPrefixes(fn))
        val oldPrefixes = state.firstName.map(
          fn => ContactManager.buildPrefixes(fn))

        context.become(ready(nState))

        // Update index
        updateIndex(newPrefixes, oldPrefixes, nState)

        sender ! nState
      }
    case lastNameUpdate: UpdateLastName =>
      persist(lastNameUpdate) { evt =>
        val nState = updateState(evt, state)

        // Building prefixes
        val newPrefixes = nState.lastName.map(
          ln => ContactManager.buildPrefixes(ln))
        val oldPrefixes = state.lastName.map(
          ln => ContactManager.buildPrefixes(ln))

        context.become(ready(nState))

        // Update index
        updateIndex(newPrefixes, oldPrefixes, nState)

        sender() ! nState
      }
    case phoneNumberUpdate: UpdatePhoneNumber =>
      persist(phoneNumberUpdate) { evt =>
        val nState = updateState(evt, state)

        // Building prefixes
        val newPrefixes = nState.phoneNumber.map(
          pn => ContactManager.buildPrefixes(pn))
        val oldPrefixes = state.phoneNumber.map(
          pn => ContactManager.buildPrefixes(pn))

        // Update actor state
        context.become(ready(nState))

        // Update index
        updateIndex(newPrefixes, oldPrefixes, nState)

        sender ! nState
      }
    case ReceiveTimeout =>
      context.parent ! ShardRegion.Passivate(stopMessage = PoisonPill)
  }

  private def updateIndex(newPrefixes: Option[Set[String]],
     oldPrefixes: Option[Set[String]], state: Contact) = {
    val contactName =
        state.firstName.getOrElse("") + state.lastName.getOrElse("")

    newPrefixes.map { nps =>
      // Adding new prefixes from index
      nps foreach { prefix =>
        index ! AddEntry(prefix, state.id, contactName, self)
      }

      // Deleting old prefixes from index
      oldPrefixes.map {
        _.diff(nps) foreach { prefix =>
          index ! RemoveEntry(prefix, state.id)
        }
      }
    }
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
  import Protocol._

  def shardName: String = "Contacts"

  def idExtractor: ShardRegion.ExtractEntityId = {
    case msg @ Get(id) => (id.value.toString, msg)
  }

  def shardResolver: ShardRegion.ExtractShardId = {
    case msg @ Get(id) => (math.abs(id.value.toString.hashCode) % 100).toString
  }

  def props(index: ActorRef) = Props(
      new ContactManager(index, UUID.random[Contact]))

  def props(index: ActorRef, id: UUID[Contact]) = Props(
      new ContactManager(index, id))

  def buildPrefixes(text: String): Set[String] = {
    1 until (text.length + 1) map {
      i => text.take(i).toString.toLowerCase
    } toSet
  }

  object Protocol {
    import utils.{Protocol => GenProto}

    sealed trait Command extends GenProto.Command with Contact.Audit

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
