package $package$

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.net.URL
import java.util.UUID
import java.time.Instant

object Json extends BaseJsonSupport

trait BaseJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UUIDFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue): UUID = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _              => deserializationError("UUID expected")
    }
  }

  implicit object InstantFormat extends RootJsonFormat[Instant] {
    def write(instant: Instant) = JsString(instant.toString)
    def read(value: JsValue): Instant = value match {
      case JsString(instant) => Instant.parse(instant)
      case _                 => deserializationError("Instant expected")
    }
  }

  implicit object URLFormat extends RootJsonFormat[URL] {
    def write(url: URL) = JsString(url.toString)
    def read(value: JsValue): URL = value match {
      case JsString(url) => new URL(url)
      case _             => deserializationError("URL expected")
    }
  }

  implicit object HttpMethodFormat extends RootJsonFormat[HttpMethod] {
    import akka.http.scaladsl.model.HttpMethods
    def write(httpMethod: HttpMethod) = JsString(httpMethod.name)
    def read(value: JsValue): HttpMethod = value match {
      case JsString(method) =>
        HttpMethods.getForKeyCaseInsensitive(method).getOrElse(deserializationError(s"Unknown HTTP method (was \$value)"))
      case _ => deserializationError("HTTP method expected")
    }
  }

  implicit val linkFormat               = jsonFormat3(Link)
  implicit val linksFormat              = jsonFormat3(Links)
  implicit val healthFormat             = jsonFormat1(Health)
}
