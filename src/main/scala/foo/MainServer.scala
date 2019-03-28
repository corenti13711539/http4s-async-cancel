package foo

import org.http4s.client.Client
import cats.temp.par._
import org.http4s.dsl.Http4sDsl
import org.http4s.client.dsl.Http4sClientDsl
import cats.effect._
import cats.implicits._
import org.http4s.HttpRoutes
import scala.language.higherKinds


class HttpServer[F[_]: ConcurrentEffect : ContextShift : Timer : Par](implicit val contextShift: ContextShift[IO], timer: Timer[IO]) extends Http4sDsl[F] with Http4sClientDsl[F] {
  import com.typesafe.scalalogging.Logger
  import com.typesafe.config.{ConfigFactory, Config}

  import com.typesafe.scalalogging.Logger
  import fs2.Stream
  import org.http4s.Uri
  import org.http4s.{MediaType, Response}
  import org.http4s.util.CaseInsensitiveString
  import org.http4s.headers.`Content-Type`
  import org.http4s.server.middleware.StaticHeaders.`no-cache`
  import org.http4s.server.middleware.CORS
  import org.http4s.client.asynchttpclient.AsyncHttpClient
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration.{Duration, SECONDS}
  import cats.data.EitherT

  val logger = Logger(this.getClass)

  val conf = ConfigFactory.load
  val BindAddress = conf.getString("service.bindAddress")
  val Port = conf.getInt("service.bindPort")
  val SessionIdHeaderKey = CaseInsensitiveString("X-Session-Id")
  val CorsAllowedOrigins = conf.getString("service.corsAllowedOrigins").replaceAll(" ", "").split(",").toSet
  val SlowApiUrl = Uri.unsafeFromString("http://localhost:8090/api/slow")

  def httpClient(): Stream[F, Client[F]] = AsyncHttpClient.stream().map { c =>
    org.http4s.client.middleware.Logger(true, false)(c)
  }

  def systemRestService() = {
    import cats.implicits._

    HttpRoutes.of[F] {
      case req@GET -> Root / "ping" => Ok("pong")
    }
  }

  def createNewBlockingPool(namePrefix: String) = {
    import java.util.concurrent.Executors
    import java.util.concurrent.ThreadFactory
    import java.util.concurrent.atomic.AtomicLong

    val blockingPool = Executors.newCachedThreadPool(
      new ThreadFactory {
        private val counter = new AtomicLong(0L)

        def newThread(r: Runnable) = {
          val th = new Thread(r)
          th.setName(namePrefix + counter.getAndIncrement.toString)
          th.setDaemon(true)
          th
        }
      })
    scala.concurrent.ExecutionContext.fromExecutor(blockingPool)
  }
  val blockingEc = createNewBlockingPool("io-thread-")


  object BlockingApi {
    import scala.concurrent.duration.FiniteDuration
    import scala.concurrent.duration._
    import cats.syntax.either._

    private def blockingApiCall() = {
      Thread.sleep(5000)
      Either.right[Throwable, String]("block, block")
    }

    private def block[A](thunk: => A, timeout: FiniteDuration): F[A] =
      contextShift.evalOn(blockingEc)(cats.effect.IO(thunk)).timeout(timeout).to[F]

    def wrappedBlockingCall = block(blockingApiCall(), 30.seconds)
  }

  object FutureBasedApi {
    private def helloFuture(name: String) = scala.concurrent.Future {
      Thread.sleep(5000)
      s"hello $name from future"
    }(blockingEc)
    def helloF(name: String) =
      IO.fromFuture(IO(helloFuture(name))).guarantee(contextShift.shift).to[F]
  }

  def slowHttpCall(hc: Client[F]) = EitherT.right[Throwable](hc.expect[String](SlowApiUrl))

  def responseOrError(r: Either[Throwable, F[Response[F]]]) = r match {
    case Left(ex) => InternalServerError(ex.getMessage)
    case Right(r) => r
  }


  def fooRestService(hc: Client[F]) =
    HttpRoutes.of[F] {
      case req@GET -> Root / "op1" =>
        (for {
          r1 <- slowHttpCall(hc)
          r2 <- slowHttpCall(hc)
        } yield Ok(r2)).value.flatMap(responseOrError)
    }

  def barRestService(hc: Client[F]) = {

    HttpRoutes.of[F] {
      case req@GET -> Root / "op2" =>
        (for {
          r1 <- EitherT.right[Throwable](FutureBasedApi.helloF("foo"))
          r2 <- EitherT(BlockingApi.wrappedBlockingCall)
          r3 <- slowHttpCall(hc)
          r4 <- EitherT.right[Throwable](Ok(r3).pure[F])
        } yield r4).value.flatMap(responseOrError)
    }
  }

  def stream: Stream[F, ExitCode] = {
    import org.http4s.server.middleware.CORS.DefaultCORSConfig
    import org.http4s.server.blaze.BlazeServerBuilder
    import org.http4s.server.Router
    import org.http4s.implicits._
    import cats.data.Kleisli
    import org.http4s.Request

    logger.info(s"server starting with config: $conf")
    val corsConfig = DefaultCORSConfig.copy(
      allowedHeaders = DefaultCORSConfig.allowedHeaders.map(_ + SessionIdHeaderKey.value),
      anyOrigin = false, allowedOrigins = CorsAllowedOrigins.contains
    )
    def cors(svc: HttpRoutes[F]) = CORS(svc, corsConfig)
    def lm(httpService: Kleisli[F, Request[F], Response[F]]) = org.http4s.server.middleware.Logger.httpApp(true, false)(httpService)

    for {
      hc <- httpClient()
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(Port, BindAddress)
        .withHttpApp(lm(Router(
          "/api/foo" -> cors(`no-cache`(fooRestService(hc))),
          "/api/bar" -> cors(`no-cache`(barRestService(hc))),
          "/api/system" -> `no-cache`(systemRestService())
        ).orNotFound))
        .serve

    } yield exitCode
  }

}

object MainServer extends IOApp {
  val httpServer = new HttpServer[IO]

  override def run(args: List[String]): IO[ExitCode] =
    httpServer.stream.compile.drain.as(ExitCode.Success)
}

