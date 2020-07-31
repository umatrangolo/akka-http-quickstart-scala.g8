package $package$ 

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._
import net.logstash.logback.argument.StructuredArguments._

object Service {
  import org.slf4j.LoggerFactory
  val logger = LoggerFactory.getLogger("access-log")

  private def startHttpServer(routes: Route, system: ActorSystem[_]) = {
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    import system.executionContext
    Http().bindAndHandle(routes, "0.0.0.0", 9000)
  }

  def main(args: Array[String]): Unit = {
    // Here we wire up the app by spawning all the actors and putting
    // them under the guardian one.
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      import akka.http.scaladsl.server.Directives._
      import cats.syntax.either._
      import Config._
      import Config.validations._

      implicit val system = context.system
      implicit val ec     = context.system.executionContext

      val service = for {

        healthcheckActor <- Either.right(context.spawn(HealthcheckActor(), "HealthcheckActor"))

        _ <- Either.right {
          context.watch(healthcheckActor)
        }

        routes <- Either.right {
          Route.seal(AccessLog.logTimedRequestResponse {
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
          system.log.error("Error while initialising service: {}", value("error", err))
          system.terminate()
          Behaviors.empty // TODO: dead code. How to avoid it?
        }, { binding =>
          val localAddress = binding.localAddress
          system.log
            .info("Server online at http://{}:{}/", value("host", localAddress.getHostString), value("port", localAddress.getPort))
          Behaviors.empty
        }
      )
    }

    implicit val system = ActorSystem[Nothing](rootBehavior, "$name$Service")

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "GoodBye") { () =>
      system.log.info("Service stopped")
      scala.concurrent.Future.successful(akka.Done)
    }
  }
}
