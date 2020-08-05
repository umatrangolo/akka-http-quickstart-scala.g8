package $package$

import scala.util.Properties
import cats.syntax.either._
import net.logstash.logback.argument.StructuredArguments._
import org.slf4j.LoggerFactory

object defs extends envdef with serdedef

trait envdef {
  private val logger = LoggerFactory.getLogger("env")

  def find[T](envKey: String, sysPropKey: String, coerce: String => Either[String, T]) =
    Properties
      .envOrNone(envKey)
      .orElse(Properties.propOrNone(sysPropKey))
      .toRight(s"config \$envKey is missing")
      .flatMap(s => coerce(s))

  def dieOrRight[T](msg: String, r: Either[String, T]) = r match {
    case Left(err) =>
      logger.error(s"\$msg: {}", v("error", err))
      sys.exit(1)
    case Right(t) => t
  }

  // Die with error code if a config is not present
  def findOrDie[T](envKey: String, sysPropKey: String, coerce: String => Either[String, T], what: String) = 
    dieOrRight(s"Error while reading \$what", find(envKey, sysPropKey, coerce))

  def notEmpty(s: String) = Either.cond(!s.trim.isEmpty(), s, "config is empty")
  def validURI(s: String) = Either.catchNonFatal(new java.net.URI(s)).leftMap(t => s"invalid URI (was: \$s) : \${t.getMessage}")
  def validURL(s: String) = Either.catchNonFatal(new java.net.URL(s)).leftMap(t => s"invalid URL (was: \$s) : \${t.getMessage}")
  def validInt(s: String) =
    Either
      .catchNonFatal(s.toInt)
      .leftMap(t => s"invalid int (was: \$s) : \${t.getMessage}")
      .filterOrElse(i => i >= 0, s"int must be gte 0 (was: \$s)")
}

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.net.URL
import java.util.UUID
import java.time.Instant

trait serdedef extends SprayJsonSupport with DefaultJsonProtocol {
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
