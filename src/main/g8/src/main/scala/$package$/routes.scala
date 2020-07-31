package $package$ 

import HealthcheckActor._
import Serde._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{StatusCodes, HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import domain._
import java.net.URL
import java.{util => ju}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.RouteResult.Rejected
import scala.concurrent.duration._

case class Link(href: String, rel: String, `type`: HttpMethod)
case class Links(
  subscriberID: String,
  subscriptionID: ju.UUID,
  links: List[Link]
)
case class Health(status: String)

object AccessLog {
  import akka.http.scaladsl.server._
  import net.logstash.logback.argument.StructuredArguments._
  import org.slf4j.LoggerFactory

  private val logger = LoggerFactory.getLogger("access-log")

  private case class ReqLogEntry(
    method: String,
    protocol: String,
    uri: String,
    remote_ip: Option[String] = None,
    remote_country: Option[String] = None
  )

  private case class RespLogEntry(
    status: String,
    code: Int
  )

  private def headerValueorNone(req: HttpRequest, name: String) =
    req.headers.find(_.lowercaseName == name.toLowerCase()).map(_.value)

  // Best effort to figure out the remote caller's IP. We start by looking
  // at the CloudFlare header to then proceed with the more or less
  // standard ones.
  private def extractRemoteIP(req: HttpRequest): Option[String] = {
    List(
      headerValueorNone(req, "cf-connecting-ip"),
      headerValueorNone(req, "X-Forwarded-For"),
      headerValueorNone(req, "RemoteAddress"),
      headerValueorNone(req, "X-Real-IP")
    ).flatten.headOption
  }
  // If we are behind CloudFlare we will be also getting the
  // originating country.
  private def extractCountry(req: HttpRequest): Option[String] = headerValueorNone(req, "Cf-Ipcountry")

  private def asLog(req: HttpRequest): ReqLogEntry =
    ReqLogEntry(
      method = req.method.name,
      protocol = req.protocol.value,
      uri = req.uri.toString,
      remote_ip = extractRemoteIP(req),
      remote_country = extractCountry(req)
    )

  private def asLog(resp: HttpResponse): RespLogEntry = RespLogEntry(status = resp.status.value, code = resp.status.intValue)

  // see: https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/debugging-directives/logRequestResult.html#building-advanced-directives
  private def akkaResponseTimeLoggingFunction(requestTimestamp: Long, req: HttpRequest, res: RouteResult) = res match {
    case Complete(resp) =>
      val responseTimestamp: Long = System.nanoTime()
      val elapsedTime: Long =
        (FiniteDuration(responseTimestamp, NANOSECONDS) - FiniteDuration(requestTimestamp, NANOSECONDS)).toMillis // convert to ms
      logger.info(
        s"""\${req.method.name} \${req.uri} \${resp.status} \$elapsedTime""",
        value("elapsed_time", elapsedTime),
        value("request", asLog(req)),
        value("response", asLog(resp))
      )
    case Rejected(reason) =>
      logger.info(
        s"Rejected Reason: \${reason.mkString(",")}",
        value("request", asLog(req))
      )
  }

  def logTimedRequestResponse = extractRequestContext.flatMap { ctx =>
    val requestTimestamp = System.nanoTime()
    mapRouteResult { resp =>
      akkaResponseTimeLoggingFunction(requestTimestamp, ctx.request, resp)
      resp
    }
  }
}

object Handlers {
  import akka.http.scaladsl.model._
  import akka.http.scaladsl.server._

  implicit val jsonifyRejectionHandler =
    RejectionHandler.default
      .mapRejectionResponse {
        case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
          // since all Akka default rejection responses are Strict this will handle all rejections
          val message = ent.data.utf8String.replaceAll("\"", """\"""")

          // we copy the response in order to keep all headers and status code, wrapping the message as hand rolled JSON
          // you could the entity using your favourite marshalling library (e.g. spray json or anything else)
          res.copy(entity = HttpEntity(ContentTypes.`application/json`, s"""{"error": "\$message"}"""))

        case x => x // pass through all other types of responses
      }
}

class HealthcheckRoutes(
  healthcheckActor: ActorRef[HealthcheckActor.Ping]
)(
  implicit val system: ActorSystem[_],
  implicit val ec: scala.concurrent.ExecutionContext
) {
  implicit val timeout = Timeout.create(system.settings.config.getDuration("routes.ask-timeout"))

  val routes = path("health") {
    get {
      complete(healthcheckActor.ask(Ping).map(p => Health(p.msg)))
    }
  }
}


