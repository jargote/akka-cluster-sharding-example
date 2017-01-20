package com.urekah.services

import com.urekah.models.Contact
import com.urekah.utils.UUID

import akka.actor.{Actor, ActorRef, ActorLogging, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding

import cats.syntax.option._
import scala.concurrent.duration._
import scala.io.Source

class Bot(index: ActorRef, contacts: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  import Bot._
  import ContactManager.Protocol._
  import Directory.Protocol._

  val directory = context.actorOf(Props(new Directory(index, contacts)))
  var tickTask: Option[Cancellable] = None
  var n = 0
  val filename = "/names.txt"
  val stream = getClass.getResourceAsStream(filename)
  val names = Source
    .fromInputStream(stream).getLines.map(_.trim).filterNot(_.isEmpty).toSeq

  // Address
  val from = Cluster(context.system).selfAddress.hostPort

  override def preStart(): Unit = {
    tickTask = context.system.scheduler.schedule(
      3.seconds, 500.millis, self, Tick).some
  }

  override def postStop(): Unit = {
    super.postStop()
    tickTask.map(t => t.cancel())
  }

  def currentName = names(n % names.size)

  def receive = create

  val create: Receive = {
    case Tick =>
      n += 1
      val id = UUID.random[Contact]
      val names = currentName.split(" ")

      println(s"Inserting $n -> Contact: $currentName")

      contacts ! Create(id)
      contacts ! UpdateFirstName(id, names(0).trim)
      contacts ! UpdateLastName(id, names(1).trim)

      context.become(searchById(id))
  }

  def searchById(id: UUID[Contact]): Receive = {
    case Tick =>
      println(s"Searching by ID: $id")

      directory ! SearchById(id)
      context.become(searchByPrefix(id))
  }

  def searchByPrefix(id: UUID[Contact]): Receive = {
    case contact: Contact =>
      val prefixes = ContactManager
        .buildPrefixes(contact.fullname)
        .filterNot(_.trim.isEmpty)

      println(s"Found contact ($contact)")

      if (prefixes.size > 0) {
        println(s"Now Searching by ${prefixes.size} prefixes")

        context.become(collectResults(contact, prefixes.size, 0))

        // Search Prefixes
        prefixes.foreach { prefix =>
          directory ! SearchByPrefix(prefix)
        }
      }
  }


  def collectResults(contact: Contact, nPrefixes: Int, found: Int): Receive = {
    case results: Seq[_] =>
      if (nPrefixes == found + 1) {
        println(s"Found contact ($contact)")

        context.become(create)
      } else {
        context.become(collectResults(contact, nPrefixes, found + 1))
      }
  }
}

object Bot {
  private case object Tick
}
