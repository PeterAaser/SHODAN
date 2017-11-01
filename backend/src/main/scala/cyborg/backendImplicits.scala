package cyborg

import fs2.internal.NonFatal
import java.nio.channels.AsynchronousChannelGroup
import scala.concurrent.ExecutionContext

// TODO: Unfuckulate this giant mess

object backendImplicits {
  import fs2._
  import java.util.concurrent.Executors

  implicit val tcpACG : AsynchronousChannelGroup = namedACG.namedACG("tcp")
  //                                                                                                        hehe
  implicit val Sch : Scheduler = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(16, threadFactoryFactoryProxyBeanFactory.mkThreadFactory("scheduler", daemon = true)))
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

object threadFactoryFactoryProxyBeanFactory {

  import java.lang.Thread.UncaughtExceptionHandler
  import java.util.concurrent.{Executors, ThreadFactory}
  import java.util.concurrent.atomic.AtomicInteger

  def mkThreadFactory(name: String, daemon: Boolean, exitJvmOnFatalError: Boolean = true): ThreadFactory = {
    new ThreadFactory {
      val idx = new AtomicInteger(0)
      val defaultFactory = Executors.defaultThreadFactory()
      def newThread(r: Runnable): Thread = {
        val t = defaultFactory.newThread(r)
        t.setName(s"$name-${idx.incrementAndGet()}")
        t.setDaemon(daemon)
        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
                                        def uncaughtException(t: Thread, e: Throwable): Unit = {
                                          ExecutionContext.defaultReporter(e)
                                          if (exitJvmOnFatalError) {
                                            e match {
                                              case NonFatal(_) => ()
                                              case fatal => System.exit(-1)
                                            }
                                          }
                                        }
                                      })
        t
      }
    }
  }
}
