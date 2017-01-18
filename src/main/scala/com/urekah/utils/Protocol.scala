package com.urekah
package utils

object Protocol {
  trait Command extends Product with Serializable

  trait Result[T] {
    def cmd: Command
    def data: T
  }
}
