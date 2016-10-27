package com.snapswap.problems

import scala.reflect.{ClassTag, classTag}
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.http.scaladsl.server._
import spray.json._

package object http {

  val `application/problem+json` = MediaType.customWithFixedCharset(
    "application", "problem+json",
    HttpCharsets.`UTF-8`
  )

  implicit def toProblematicThrowable[T <: Throwable](t: T): ProblematicThrowable[T] = new ProblematicThrowable(t)

  implicit def toProblematicRejection[R <: Rejection](r: R): ProblematicRejection[R] = new ProblematicRejection(r)

  private implicit def problemJsonMarshallerConverter[T](writer: RootJsonWriter[T])(implicit printer: JsonPrinter): ToEntityMarshaller[T] =
    problemJsonMarshaller[T](writer, printer)

  private implicit def problemJsonMarshaller[T](implicit writer: RootJsonWriter[T], printer: JsonPrinter): ToEntityMarshaller[T] =
    problemJsValueMarshaller compose writer.write

  private implicit def problemJsValueMarshaller(implicit printer: JsonPrinter): ToEntityMarshaller[JsValue] =
    Marshaller.StringMarshaller.wrap(`application/problem+json`)(printer)

  private def supports[T <: Throwable](t: T)(implicit maker: T => Problem): Boolean = maker match {
    case pf: PartialFunction[T, Problem] => pf.isDefinedAt(t)
    case _ => true
  }

  /**
    * Provides a handler to exceptions of class T and only T
    *
    * @param maker - implicit Problem constructor
    * @tparam T - concrete type of exceptions to handle
    * @return exceptions handler
    */
  def handleOnly[T <: Throwable : ClassTag](implicit maker: T => Problem, log: LoggingAdapter, printer: JsonPrinter = CompactPrinter): ExceptionHandler = ExceptionHandler {
    case ex if classTag[T] == ClassTag(ex.getClass) && supports(ex.asInstanceOf[T]) =>
      log.error(ex, ex.getMessage)
      val p = ex.asInstanceOf[T].toProblem
      complete {
        (p.status, p.toJson)
      }
  }

  /**
    * Provides a handler to exceptions of class T and any child class of T
    *
    * @param maker - implicit Problem constructor
    * @tparam T - base type of exceptions to handle
    * @return exceptions handler
    */
  def handleException[T <: Throwable : ClassTag](implicit maker: T => Problem, log: LoggingAdapter, printer: JsonPrinter = CompactPrinter): ExceptionHandler = {
    val clazz: Class[_] = classTag[T].runtimeClass

    ExceptionHandler {
      case ex if clazz.isAssignableFrom(ex.getClass) && supports(ex.asInstanceOf[T]) =>
        log.error(ex, ex.getMessage)
        val p = ex.asInstanceOf[T].toProblem
        complete {
          (p.status, p.toJson)
        }
    }
  }

  /**
    * Provides a common exceptions handler that converts any exception to InternalProblem
    *
    * @param hideActualError if true then InternalProblem doesn't contain any information about actual error, otherwise "problem.details" contains exception message
    * @param log             - implicit logging adapter
    * @return exceptions handler
    */
  def handleAny(hideActualError: Boolean = true)(implicit log: LoggingAdapter, printer: JsonPrinter = CompactPrinter): ExceptionHandler = ExceptionHandler {
    case t: Throwable =>
      log.error(t, t.getMessage)
      complete {
        val problem: Problem = if (hideActualError) InternalProblem() else InternalProblem(t.getMessage)
        (problem.status, problem.toJson)
      }
  }

  def handleRejection[R <: Rejection : ClassTag](implicit log: LoggingAdapter, maker: R => Problem, printer: JsonPrinter = CompactPrinter): PartialFunction[Rejection, Route] = {
    case r if classTag[R] == ClassTag(r.getClass) =>
      val p = r.asInstanceOf[R].toProblem

      log.warning(s"Rejection reason: ${p.toString}")

      complete {
        (p.status, p.toJson)
      }
  }

  def handleRejections(pf: PartialFunction[Rejection, Route])(implicit log: LoggingAdapter, printer: JsonPrinter = CompactPrinter): RejectionHandler = {
    RejectionHandler
      .newBuilder()
      .handle(pf orElse defaultRejectionsHandler)
      .handleNotFound {
        complete {
          (NotFound.status, NotFound.asInstanceOf[Problem].toJson)
        }
      }
      .result()
  }

  private def defaultRejectionsHandler(implicit log: LoggingAdapter, printer: JsonPrinter = CompactPrinter): PartialFunction[Rejection, Route] = {
    case MalformedParameterRejection(parameterName, parameterValue, reason, correctValueExample) =>
      val p: Problem = MalformedParameter(parameterName, parameterValue, reason, correctValueExample)

      log.warning(s"Rejection reason: ${p.detail}")

      complete {
        (p.status, p.toJson)
      }
    case ValidationRejection(msg, _) =>
      val p: Problem = ValidationFailed(msg.stripPrefix("requirement failed:").trim)

      log.warning(s"Rejection reason: $msg")

      complete {
        (p.status, p.toJson)
      }
    case AuthenticationFailedRejection(cause, challenge) =>
      val p: Problem = AuthenticationFailed

      log.warning(s"Rejection reason: $cause")

      complete {
        (p.status, p.toJson)
      }
    case MalformedRequestContentRejection(msg, _) =>
      val p: Problem = MalformedRequest("Malformed request: " + msg)

      log.warning(s"Rejection reason: $msg")

      complete {
        (p.status, p.toJson)
      }
  }
}
