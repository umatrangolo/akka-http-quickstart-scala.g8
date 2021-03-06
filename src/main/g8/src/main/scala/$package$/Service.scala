package $package$

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.server.Route
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments._

object Service {
  val logger = LoggerFactory.getLogger(this.getClass)

  private def startHttpServer(routes: Route, system: ActorSystem[_]) = {
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    Http().newServerAt("0.0.0.0", 9000).bind(routes)
  }

  def main(args: Array[String]): Unit = {

    // Here we wire up the app by spawning all the actors and putting
    // them under the guardian one.
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      import akka.http.scaladsl.server.Directives._
      import cats.syntax.either._

      implicit val system = context.system
      implicit val ec     = context.system.executionContext

      val service = for {
        healthcheckActor <- Either.right(context.spawn(HealthcheckActor(), "HealthcheckActor"))
        _ <- Either.right {
          context.watch(healthcheckActor)
        }

        routes <- Either.right {
          Route.seal(Access.logTimedRequestResponse {
            handleRejections(Handlers.jsonifyRejectionHandler) {
              concat(
                new HealthcheckRoutes(healthcheckActor).routes,
              )
            }
          })
        }

        binding <- Either
          .catchNonFatal(Await.result(startHttpServer(routes, system), 10.seconds))
          .leftMap(t => s"Error starting server: \${t.getMessage}")
      } yield binding

      service.fold(
        { err =>
          logger.error("Error while initializing service: {}", value("error", err))
          system.terminate()
          Behaviors.empty // TODO: dead code. How to avoid it?
        }, { binding =>
          val localAddress = binding.localAddress
          logger.info("Server online at http://{}:{}/", value("host", localAddress.getHostString), value("port", localAddress.getPort))
          Behaviors.empty
        }
      )
    }

    implicit val system = ActorSystem[Nothing](rootBehavior, "$name$")

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "GoodBye") { () =>
      logger.info("Service stopped")
      scala.concurrent.Future.successful(akka.Done)
    }
  }
}
