package com.cyborg


object httpClient {

  import io.circe.literal._
  import io.circe.generic.auto._
  import io.circe.syntax._

  import fs2._
  import cats.effect.IO
  import cats.effect._

  import org.http4s._
  import org.http4s.client._
  import org.http4s.dsl._
  import org.http4s.client.blaze._
  import org.http4s.Uri
  import org.http4s.server.blaze.BlazeBuilder
  import org.http4s.circe._

  val httpClient = PooledHttp1Client[IO]()
  implicit val decoder = jsonOf[IO, DAQparams]
  case class DAQparams(samplerate: Int, segmentLength: Int, selectChannels: List[Int])

  def connectDAQrequest(params: DAQparams): IO[String] = {
    val req = POST(Uri.uri("http://localhost:8080/"), params.asJson)
    httpClient.expect[String](req)
  }

  def startDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://localhost:8080/")))

  def stopDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://localhost:8080/")))

  def sayHello: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://localhost:8080/")))


  import params.experiment._

  def startMEAMEserver: IO[String] = {
    val daqParams = DAQparams(samplerate, segmentLength, List(1,2,3))
    connectDAQrequest(daqParams).attempt flatMap {
      case Left(e) => IO.pure("Something went wrong when connecting to the DAQ")
      case Right(_) => startDAQrequest flatMap {
        resp => {
          IO.pure(s"Looks like we're good. resp was $resp")
        }
      }
    }
  }



  // def someTests(): Unit = {

  //   val jsonService: HttpService[IO] = HttpService {
  //     case req @ POST -> Root / "hello" =>
  //       for {
	//         // Decode a User request
	//         user <- req.as[DAQparams]
	//         // Encode a hello response
	//         resp <- Ok("Cool beans")
  //       } yield (resp)
  //   }

  //   val builder = BlazeBuilder[IO].bindHttp(8080).mountService(jsonService, "/")
  //   builder.start.unsafeRunSync

  //   val myReq = DAQparams(10000, 2000, List(1, 2, 3))

  //   val hello = connectDAQrequest(myReq).unsafeRunSync()
  //   println(hello)


  // }

  // object MEAMEmocks {
  //   val jsonService: HttpService[IO] = HttpService {
  //     case req @ POST -> Root / "hello" =>
  //       for {
	//         // Decode a User request
	//         user <- req.as[DAQparams]
	//         // Encode a hello response
	//         resp <- Ok("Cool beans")
  //       } yield (resp)
  //     case a: Any => {
  //       println(a)
  //       Ok("Uncool beans")
  //     }
  //   }
  // }
}
