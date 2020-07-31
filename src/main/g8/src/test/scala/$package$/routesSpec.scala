package $package$ 

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import domain._
import java.net.URL
import java.time.Instant
import java.util.UUID
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.prop._
import pdi.jwt.JwtAlgorithm.HS384
import pdi.jwt._
import repository._
import scala.concurrent._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.annotation.nowarn

@nowarn class RoutesSpec
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
