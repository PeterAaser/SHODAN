package com.cyborg

import java.nio.channels.AsynchronousChannelGroup

object backendImplicits {
  import fs2._
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val tcpACG : AsynchronousChannelGroup = namedACG.namedACG("tcp")
}

object namedACG {

  /**
    Lifted verbatim from fs2 tests.
    I have no idea what it does, but it makes stuff work...
    */

  import java.nio.channels.AsynchronousChannelGroup
  import java.lang.Thread.UncaughtExceptionHandler
  import java.nio.channels.spi.AsynchronousChannelProvider
  import java.util.concurrent.ThreadFactory
  import java.util.concurrent.atomic.AtomicInteger

  def namedACG(name:String):AsynchronousChannelGroup = {
    val idx = new AtomicInteger(0)
    AsynchronousChannelProvider.provider().openAsynchronousChannelGroup(
      16
        , new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"fs2-AG-$name-${idx.incrementAndGet() }")
          t.setDaemon(true)
          t.setUncaughtExceptionHandler(
            new UncaughtExceptionHandler {
              def uncaughtException(t: Thread, e: Throwable): Unit = {
                println("----------- UNHANDLED EXCEPTION ---------")
                e.printStackTrace()
              }
            })
          t
        }
      }
    )
  }
}
