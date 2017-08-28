package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Queue
import fs2.util.Async
import scala.language.higherKinds
import com.typesafe.config._
import utilz._

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  // TODO rename to spike detector something?
  def assembleInputFilter[F[_]:Async](
    broadcastSource: List[dataTopic[F]],
    channels: List[Channel],
    spikeDetector: Pipe[F,Int,Double]
  ): Stream[F,ffANNinput] = {

    // selects relevant topics and subscribe to them
    val inputTopics = channels.map(broadcastSource(_))
    val channelStreams = inputTopics.map(_.subscribe(100))

    // TODO Does not synchronize streams
    // This means, if at subscription time, one channel has newer input,
    // this discrepancy will never be resolved and one stream will be permanently ahead
    val spikeChannels = channelStreams
      .map(_.map(_._1))
      .map(_.through(chunkify))
      .map(_.through(spikeDetector))

    Stream.emit(spikeChannels).through(roundRobin).map(_.toVector)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.
    */
  def broadcastDataStream[F[_]:Async](
    source: Stream[F,Int],
    topics: List[dataTopic[F]]): Stream[F,Unit] =
  {

    var throttle = 0
    import params.experiment._

    def publishSink(topics: List[dataTopic[F]]): Sink[F,Int] = h => {
      def loop(segmentId: Int, topics: List[dataTopic[F]]): Handle[F,Int] => Pull[F,F[Unit],Unit] = h => {
        h.awaitN(segmentLength*totalChannels, false) flatMap {
          case (chunks, h) => {
            if(throttle == 0)
              println("broadcasting")
            throttle = (throttle + 1) % 20
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
    source.through(publishSink(topics))
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    */
  def assembleTopics[F[_]:Async]: Stream[F,(dbDataTopic[F],meameDataTopic[F])] = {

    // hardcoded
    val dbChannels = 60
    val meameChannels = 60

    createTopics(60, (Vector.empty[Int],-1)) flatMap {
      dbTopics => {
        createTopics(60, (Vector.empty[Int],-1)) map {
          meameTopics => {
            (dbTopics, meameTopics)
          }
        }
      }
    }

    // for {
    //   dbTopics <- createTopics[F,dataSegment](dbChannels, (Vector.empty[Int],-1))
    //   meameTopics <- createTopics[F,dataSegment](meameChannels, (Vector.empty[Int],-1))
    // } yield ((dbTopics, meameTopics))
  }


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME
    */
  def assembleGA[F[_]:Async](
    dataSource: List[dataTopic[F]],
    inputChannels: List[Channel],
    outputChannels: List[Channel],
    frontendAgentObserver: Sink[F,Agent],
    feedbackSink: Sink[F,Byte]): Stream[F,Unit] =
  {

    import params.experiment._
    import params.filtering._

    def filter = spikeDetector.spikeDetectorPipe[F](samplerate, MAGIC_THRESHOLD)
    // def filter = (a: Stream[F,Int]) => Stream[F,Double](0.0).repeat

    def inputSpikes = assembleInputFilter(dataSource, inputChannels, filter)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = MEAMEutilz.logScaleBuilder(scala.math.E)
      MEAMEutilz.toStimFrequency(outputChannels, logScaler)
    }

    val experimentPipe = GApipes.experimentPipe(inputSpikes, params.filtering.layout)

    experimentPipe
      .through(pipe.observeAsync(_, 10000)(frontendAgentObserver))
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)
      .through(feedbackSink)
  }


  /**
    Takes data from the dataTopic list, filters the data, vectorizes it to fit message size
    before demuxing it to a single stream which is sent to the visualizer
    */
  // TODO where should data filtering really be handled?
  def assembleWebsocketVisualizer[F[_]:Async](
    dataSource: List[dataTopic[F]],
    dataFilter: Pipe[F,Int,Int]
  ): Stream[F, Unit] =
  {

    import params.waveformVisualizer.wfMsgSize
    val mapped = dataSource
      .map(_.subscribe(1000))
      .map(_.map(_._2))
      .map(_.through(dataFilter))
      .map(_.through(utilz.vectorize(wfMsgSize)))

    val muxed = Stream.emit(mapped).through(roundRobin).through(chunkify)
    wsIO.webSocketWaveformSink(muxed)
  }


  /**
    Assembles a simple test to help unclog the pipes.
    Please ask a professional before using plumbo on your own pipes.
    If you put it in a plastic bottle with some water and shake it it will explode, I sure miss being 13
    */
  def plumbo[F[_]:Async](
      dataSource: List[dataTopic[F]]
    , inputChannels: List[Channel]
    // , outputChannels: List[Channel]
    // , frontendAgentObserver: Sink[F,Agent]
    // , feedbackSink: Sink[F,Byte]
  ): Stream[F,Unit] =
  {
    Stream.empty
  }
}
