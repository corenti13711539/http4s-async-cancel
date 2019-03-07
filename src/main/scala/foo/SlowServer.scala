package foo

import cats.temp.par._
import org.http4s.dsl.Http4sDsl
import cats.effect._
import cats.implicits._
import org.http4s.HttpRoutes
import scala.language.higherKinds


class SlowHttpServer[F[_]: ConcurrentEffect : ContextShift : Timer : Par] extends Http4sDsl[F] {
  import com.typesafe.scalalogging.Logger
  import fs2.Stream
  import org.http4s.server.middleware.StaticHeaders.`no-cache`

  val Port = 8090
  val BindAddress = "localhost"
  val SleepTime = 5000

  val logger = Logger(this.getClass)

  def slowRestService() = HttpRoutes.of[F] {
    case GET -> Root / "slow" =>
      Thread.sleep(SleepTime)
      Ok("still there?")
  }

  def stream: Stream[F, ExitCode] = {
    import org.http4s.server.blaze.BlazeServerBuilder
    import org.http4s.server.Router
    import org.http4s.implicits._
    import org.http4s.server.middleware.Logger.httpApp

    for {
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(Port, BindAddress)
        .withHttpApp(httpApp(true, false)(Router(
          "/api" -> `no-cache`(slowRestService()),
        ).orNotFound))
        .serve
    } yield exitCode
  }

}

object SlowServer extends IOApp {
  val slowServer = new SlowHttpServer[IO]

  override def run(args: List[String]): IO[ExitCode] =
    slowServer.stream.compile.drain.as(ExitCode.Success)
}
