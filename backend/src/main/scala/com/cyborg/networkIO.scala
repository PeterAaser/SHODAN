package com.cyborg

import com.cyborg.params._
import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.io.tcp._

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import java.net.InetSocketAddress

import scala.language.higherKinds
import com.typesafe.config._

object networkIO {

  import namedACG._

  implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

  val conf = ConfigFactory.load()
  val netConf = conf.getConfig("io")
  val addressConf = netConf.getConfig(netConf.getString("target"))

  val ip = addressConf.getString("ip")
  val port = addressConf.getInt("port")
  val socketAddress = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = netConf.getInt("sendBufSize")
  val receiveBufferSize = netConf.getInt("recvBufSize")
  val keepAlive = true
  val noDelay = true

  val maxQueued = 3

  def socketStream[F[_]: Async]: Stream[F, Socket[F]] =
    client(
      socketAddress,
      reuseAddress,
      sendBufferSize,
      receiveBufferSize,
      keepAlive,
      noDelay)


  def rawDataStream(socket: Socket[Task]): Stream[Task,Int] =
    socket.reads(1024*1024)
      .through(utilz.bytesToInts)


  def decodeChannelStreams(dataStream: Stream[Task,Int], segmentLength: Int, nChannels: Int = 60): Stream[Task,Vector[Stream[Task,Int]]] =
    utilz.alternator(dataStream, segmentLength, nChannels)


  object msgTest {

    import scodec._
    import scodec.bits._
    import codecs._

    import Attempt._

    import scodec.stream._


    def roundtrip[A](a: A)(implicit C: Codec[A]) = {
      C.encode(a) match {
        case Failure(error) =>
          println("FUGG!")
        case Successful(encoded) =>
          C.decode(encoded) match {
            case Failure(error) =>
              println("FUGG!")
            case Successful(DecodeResult(decoded, remainder)) =>
              println(decoded)
          }
      }
    }

    def doSomeMagic[F[_],A](a: A)(implicit C: Codec[A]): Stream[F,Byte] = {
      C.encode(a) match {
        case Failure(error) => {
          println("FYY FAEN!")
          Stream.empty
        }
        case Successful(encoded) => {
          val meme = encoded.bytes ++ encoded.bytes
          Stream.emits(meme.toSeq)
        }
      }
    }

    val runme = "hi how are you :)"

  }

}
