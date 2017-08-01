package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import fs2.util.Async
import scala.language.higherKinds
import com.typesafe.config._
import utilz._

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  // Needs a sweep size and a spike detector
  def assembleInputFilter[F[_]:Async](channels: List[Int]): Pipe[F, Int, ffANNinput] = neuroStream => {

    val conf = ConfigFactory.load()
    val experimentConf = conf.getConfig("experimentConf")

    import params.filtering._
    import params.experiment._


    val spikeDetectorPipe: Pipe[F, Int, Double] =
      spikeDetector.spikeDetectorPipe(samplerate, MAGIC_THRESHOLD)

    val neuronChannelsStream: Stream[F,Vector[Stream[F,Int]]] =
      alternator(neuroStream, segmentLength, channels.length, 10000)


    val spikeStream = neuronChannelsStream flatMap {
      streams: Vector[Stream [F,Int]] => {

        val spikeChannels: Vector[Stream[F, Double]] = streams.toVector
          .map((λ: Stream[F,Int]) => λ.through(spikeDetectorPipe))

        val spikeTrains =
          (Stream[F, Vector[Double]](Vector[Double]()).repeat /: spikeChannels){
            (b: Stream[F, Vector[Double]], a: Stream[F, Double]) => b.zipWith(a){
              (λ, µ) => µ +: λ
            }
          }
        spikeTrains
      }
    }
    spikeStream
  }


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  def assembleInputFilter2[F[_]:Async](
    dataSource: Stream[F,List[dataTopic[F]]],
    channels: List[Channel],
    spikeDetector: Pipe[F,Int,Double]
  ): Stream[F,ffANNinput] = {

    val channelStreams: Stream[F,List[Stream[F,dataSegment]]] = for {
      topics <- dataSource
    } yield {

      // Select the topics we are interested in
      val inputTopics = channels.map(topics(_))
      inputTopics.map(_.subscribe(100)) // 100 is arbitrarily chosen
    }


    // TODO does not check if streams are synchronized.
    channelStreams flatMap {
      streams: List[Stream[F,(Vector[Int],Int)]] => {

        val spikeChannels = streams
          .map(_.map(_._1))
          .map(_.through(chunkify))
          .map(_.through(spikeDetector))

        Stream.emit(spikeChannels).through(roundRobin).map(_.toVector)
      }
    }

  }


  def assembleAgentPipe[F[_]: Async](ff: Filters.FeedForward): Pipe[F, ffANNinput, Agent] = ffInput => {
    val FF = Filters.ANNPipes.ffPipe[F](ff)
    val gamePipe = agentPipe.wallAvoidancePipe[F]()

    ffInput.through(FF).through(gamePipe)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.
    */
  def broadcastDataStream[F[_]:Async](
    source: Stream[F,Int],
    topics: Stream[F,List[dataTopic[F]]]): Stream[F,Unit] =
  {

    import params.experiment._

    def publishSink(topics: List[dataTopic[F]]): Sink[F,Int] = h => {
      def loop(segmentId: Int, topics: List[dataTopic[F]]): Handle[F,Int] => Pull[F,F[Unit],Unit] = h => {
        h.awaitN(segmentLength*totalChannels, false) flatMap {
          case (chunks, h) => {
            val flattened = Chunk.concat(chunks).toVector
            val grouped = flattened.grouped(segmentLength)
            val stamped = grouped.map((_,segmentId)).toList
            val broadCasts = stamped.zip(topics).map{
              case(segment, topic) => topic.publish1(segment)
            }

            // TODO when fs2 cats integration hits traverse broadcasts and use Pull.eval
            Pull.output(Chunk.seq(broadCasts)) >> loop(segmentId + 1, topics)(h)
          }
        }
      }
      concurrent.join(totalChannels)(h.pull(loop(0, topics)).map(Stream.eval))
    }

    // Should ideally emit a list of topics doing their thing
    topics flatMap { topics =>
      source.through(publishSink(topics))
    }
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    */
  def assembleTopics[F[_]:Async]: Stream[F,(dbDataTopic[F],meameDataTopic[F])] = {

    // hardcoded
    val dbChannels = 60
    val meameChannels = 60

    for {
      dbTopics <- createTopics[F,dataSegment](dbChannels, (Vector.empty[Int],-1))
      meameTopics <- createTopics[F,dataSegment](meameChannels, (Vector.empty[Int],-1))
    } yield ((dbTopics, meameTopics))
  }
}
