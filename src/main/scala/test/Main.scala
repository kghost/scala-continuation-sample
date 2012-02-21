package test

import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.ByteBuffer

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.continuations.reset
import scala.util.continuations.shift
import scala.util.continuations.shiftUnit

object Main extends App {
  val selector = Selector.open()
  val server = ServerSocketChannel.open()
  server.socket().bind(new InetSocketAddress(12345))
  server.configureBlocking(false)
  reset {
    while (true) {
      server.accept() match {
        case c: SocketChannel =>
          reset {
            println("Accept: " + c)
            c.configureBlocking(false)
            while (c.isOpen && c.isConnected) {
              val bb = ByteBuffer.allocateDirect(1024)
              c.read(bb) match {
                case count if count > 0 =>
                  println("Read: " + c + " count: " + count)
                  bb.flip
                  while (bb.hasRemaining) {
                    c.write(bb) match {
                      case count if count > 0 =>
                        println("Write: " + c + " count: " + count)
                        shiftUnit[Unit, Unit, Unit]()
                      case count if count == 0 =>
                        println("WriteBlock: " + c)
                        shift[Unit, Unit, Unit] { cont =>
                          c.register(selector, SelectionKey.OP_WRITE, cont)
                        }
                      case _ =>
                        println("WriteError: " + c)
                        bb.clear()
                        c.close()
                        shiftUnit[Unit, Unit, Unit]()
                    }
                  }
                case count if count == 0 =>
                  println("ReadBlock: " + c)
                  shift[Unit, Unit, Unit] { cont =>
                    c.register(selector, SelectionKey.OP_READ, cont)
                  }
                case _ =>
                  println("ReadError: " + c)
                  c.close()
                  shiftUnit[Unit, Unit, Unit]()
              }
            }
          }
          shiftUnit[Unit, Unit, Unit]()
        case null =>
          println("AcceptBlock")
          shift[Unit, Unit, Unit] { cont =>
            server.register(selector, SelectionKey.OP_ACCEPT, cont)
          }
      }
      shiftUnit[Unit, Unit, Unit]()
    }
  }

  val keys = selector.selectedKeys
  while (true) {
    selector.select
    keys foreach { k =>
      k.interestOps(0)
      k.attachment.asInstanceOf[Function1[Unit, Unit]].apply(Unit)
    }
    keys.clear
  }
}
