package com.snapswap.problems

import akka.http.scaladsl.server.Rejection
import akka.http.scaladsl.model.{StatusCodes, StatusCode}
import spray.json._

/**
  * Problem defines a way to return detailed yet unified error response from an API endpoint
  * The implementation follows <a href="https://tools.ietf.org/html/rfc7807">Problem Details for HTTP APIs</a>
  */
trait Problem {

  /**
    * Problem type as an URI (a better way to define an error code). When the URI dereferenced, it is encouraged to provide human-readable documentation for the problem type (e.g., using HTML)
    *
    * @return an URI reference that identifies the problem type
    */
  def `type`: String = "about:blank"

  /**
    * @return HTTP status code generated by the origin server for this occurrence of the problem.
    */
  def status: StatusCode

  /**
    * @return a short, human-readable summary of the problem type. Doesn't change from occurrence to occurrence of the problem
    */
  def title: String = status.reason()

  /**
    * @return An human readable explanation specific to this occurrence of the problem.
    */
  def detail: String = status.defaultMessage()

}

case class InternalProblem(override val detail: String) extends Problem {
  override val status: StatusCode = InternalProblem.status
}

object InternalProblem {
  val status: StatusCode = StatusCodes.InternalServerError

  def apply(): Problem = InternalProblem(status.defaultMessage())
}

case object NotFound extends Problem {
  override val status: StatusCode = StatusCodes.NotFound
}

case class ValidationFailed(override val detail: String) extends Problem {
  override val status: StatusCode = StatusCodes.BadRequest
}

case class MalformedRequest(override val detail: String) extends Problem {
  override val status: StatusCode = StatusCodes.BadRequest
}

case class MalformedParameterRejection(parameterName: String, parameterValue: String, reason: Option[String] = None, correctValueExample: Option[String] = None) extends Rejection

case class MalformedParameter(parameterName: String, parameterValue: String, reason: Option[String] = None, correctValueExample: Option[String] = None) extends Problem {
  def this(parameterName: String, parameterValue: String, reason: String) = this(parameterName, parameterValue, reason = Some(reason))

  override val status: StatusCode = StatusCodes.BadRequest
  override val detail = s"The parameter '$parameterName' was malformed: '$parameterValue'." + reason.fold("")(s => s" $s.") + correctValueExample.fold("")(s => s" Example: '$s'.")
}

case object AuthenticationFailed extends Problem {
  override val status: StatusCode = StatusCodes.Unauthorized
}

object Problem extends DefaultJsonProtocol {

  implicit object ProblemJsonWriter extends RootJsonWriter[Problem] {
    def write(p: Problem): JsValue = {
      JsObject(
        Map(
          "type" -> JsString(p.`type`),
          "title" -> JsString(p.title),
          "status" -> JsNumber(p.status.intValue),
          "detail" -> JsString(p.detail)
        )
      )
    }
  }

}

private[problems] class ProblematicThrowable[T <: Throwable](t: T) {
  def toProblem(implicit maker: T => Problem) = maker(t)
}

private[problems] class ProblematicRejection[R <: Rejection](r: R) {
  def toProblem(implicit maker: R => Problem) = maker(r)
}
