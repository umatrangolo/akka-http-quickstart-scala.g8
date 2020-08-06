package $package$

import HealthcheckActor._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import java.{util => ju}
import akka.http.scaladsl.model.HttpMethod

case class Link(href: String, rel: String, `type`: HttpMethod)
case class Links(
  subscriberID: String,
  subscriptionID: ju.UUID,
  links: List[Link]
)
case class Health(status: String)

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

import Json._

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


