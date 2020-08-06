package $package$

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.prop._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RoutesSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with ScalatestRouteTest
    with TableDrivenPropertyChecks {

  lazy val testKit = ActorTestKit()

  implicit def typedSystem = testKit.system
  implicit val clock       = java.time.Clock.systemUTC

  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.toClassic

  val healthcheckActor = testKit.spawn(HealthcheckActor())

  "health" should {
    val routes = new HealthcheckRoutes(healthcheckActor).routes
    "return 200 OK" in {
      Get("/health") ~> routes ~> check {
        status should ===(StatusCodes.OK)
      }
    }
  }
}
