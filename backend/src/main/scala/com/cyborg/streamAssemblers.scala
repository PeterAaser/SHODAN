package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import java.io.File
import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import utilz._
import cats.effect.IO
import cats.effect.Effect

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  // TODO rename to spike detector something?
  def assembleInputFilter[F[_]: Effect](
    broadcastSource: List[DataTopic[F]],
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

    Stream.emit(spikeChannels).covary[F].through(roundRobin).map(_.toVector)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.
    */
  def broadcastDataStream(
    source: Stream[IO,Int],
    topics: List[DataTopic[IO]])(implicit ec: ExecutionContext): Stream[IO,Unit] = {

    import params.experiment._

    def publishSink(topics: List[DataTopic[IO]]): Sink[IO,Int] = {
      def loop(segmentId: Int, topics: List[DataTopic[IO]], s: Stream[IO,Int]): Pull[IO,IO[Unit],Unit] = {
        s.pull.unconsN(segmentLength*totalChannels.toLong, false) flatMap {
          case Some((seg, tl)) => {
            val grouped = seg.toVector.grouped(segmentLength)
            val stamped = grouped.map((_,segmentId)).toList
            val broadCasts = stamped.zip(topics).map{
              case(segment, topic) => topic.publish1(segment)
            }

            // TODO when fs2 cats integration hits traverse broadcasts and use Pull.eval
            // Pull.output(Chunk.seq(broadCasts)) >> loop(segmentId + 1, topics)(h)
            import cats.implicits._

            Pull.output(Segment.seq(broadCasts)) >> loop(segmentId + 1, topics, tl)
          }
        }
      }
      in => loop(0, topics, in).stream.map(Stream.eval).join(totalChannels)
    }

    // Should ideally emit a list of topics doing their thing
    source.through(publishSink(topics))
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    */
  def assembleTopics[F[_]: Effect](implicit ec: ExecutionContext): Stream[F,(DbDataTopic[F],MeameDataTopic[F])] = {

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
  }


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME
    */
  def assembleGA[F[_]: Effect](
    dataSource: List[DataTopic[F]],
    inputChannels: List[Channel],
    outputChannels: List[Channel],
    frontendAgentObserver: Sink[F,Agent],
    feedbackSink: Sink[F,Byte])(implicit ec: ExecutionContext): Stream[F,Unit] =
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
      .observeAsync(10000)(frontendAgentObserver)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)
      .through(feedbackSink)
  }


  /**
    Takes data from the DataTopic list, filters the data, vectorizes it to fit message size
    before demuxing it to a single stream which is sent to the visualizer
    */
  // TODO where should data filtering really be handled?
  def assembleWebsocketVisualizer[F[_]: Effect](
    dataSource: List[DataTopic[F]],
    dataFilter: Pipe[F,Int,Int])(implicit ec: ExecutionContext): Stream[F, Unit] =
  {
    import params.waveformVisualizer.wfMsgSize
    val mapped = dataSource
      .map(_.subscribe(1000))
      .map(_.map(_._2))
      .map(_.through(dataFilter))
      .map(_.through(utilz.vectorize(wfMsgSize)))

    val muxed = Stream.emit(mapped).covary[F].through(roundRobin).through(chunkify)
    wsIO.webSocketWaveformSink(muxed)
  }


  def assembleMcsFileReader(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val theThing = mcsParser.eatDirectory(new File("/home/peteraa/Fuckton_of_MEA_data/hfd5_test").toPath())
    theThing
  }
}
