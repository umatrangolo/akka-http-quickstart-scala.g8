package $package$

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.RouteResult.{ Complete, Rejected }
import scala.concurrent.duration._
import akka.http.scaladsl.server._
import net.logstash.logback.argument.StructuredArguments._
import org.slf4j.LoggerFactory

object Access {
  private val logger = LoggerFactory.getLogger("access")

  private object domain {
    case class ReqLogEntry(
      method: String,
      protocol: String,
      uri: String,
      remote_ip: Option[String] = None,
      remote_country: Option[String] = None
    )

    case class RespLogEntry(
      status: String,
      code: Int
    )
  }

  import domain._

  private def headerValueOrNone(req: HttpRequest, name: String) =
    req.headers.find(_.lowercaseName == name.toLowerCase()).map(_.value)

  // Best effort to figure out the remote caller's IP. We start by looking
  // at the CloudFlare header to then proceed with the more or less
  // standard ones.
  private def extractRemoteIP(req: HttpRequest): Option[String] = {
    List(
      headerValueOrNone(req, "cf-connecting-ip"),
      headerValueOrNone(req, "X-Forwarded-For"),
      headerValueOrNone(req, "RemoteAddress"),
      headerValueOrNone(req, "X-Real-IP")
    ).flatten.headOption
  }

  // If we are behind CloudFlare we will be also getting the
  // originating country.
  private def extractCountry(req: HttpRequest): Option[String] = headerValueOrNone(req, "Cf-Ipcountry")

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
