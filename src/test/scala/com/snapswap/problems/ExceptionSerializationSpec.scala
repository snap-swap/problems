package com.snapswap.problems

import scala.concurrent.Future
import scala.util.control.NoStackTrace
import akka.event.{LoggingAdapter, NoLogging}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.{Route, Rejection, ValidationRejection}
import spray.json.{JsonPrinter, PrettyPrinter}
import org.scalatest.{WordSpec, Matchers}

class ExceptionSerializationSpec extends WordSpec with Matchers with ScalatestRouteTest {

  implicit val log: LoggingAdapter = NoLogging
  implicit val printer: JsonPrinter = PrettyPrinter

  object problemsSetup {

    case class SampleException1(id: Int) extends NoStackTrace
    case class SampleException2(id: Int) extends NoStackTrace

    trait ParentException extends NoStackTrace {
      def id: Int
    }
    case class ChildException3(id: Int) extends ParentException

    abstract class AbstractSampleProblem(problemType: String, id: Int) extends Problem {
      override val `type` = s"http://sample.com/errors/$problemType"
      override val title = "Sample problem"
      val status = StatusCodes.BadRequest
      override val detail = s"sample problem with id = $id"
    }

    case class SampleProblem1(id: Int) extends AbstractSampleProblem("sample1", id)
    case class SampleProblem2(id: Int) extends AbstractSampleProblem("sample2", id)
    case class SampleProblem3(id: Int) extends AbstractSampleProblem("sample3", id)

    implicit def toProblem1(ex: SampleException1): Problem = SampleProblem1(ex.id)
    implicit def toProblem2(ex: SampleException2): Problem = SampleProblem2(ex.id)
    implicit def toProblem3(ex: ParentException): Problem = SampleProblem3(ex.id)

    def throwSampleException1: Future[String] = Future.failed(SampleException1(1))
    def throwSampleException2: Future[String] = Future.failed(SampleException2(2))
    def throwChildException: Future[String] = Future.failed(ChildException3(3))
    def throwRuntimeException: Future[String] = Future.failed(new RuntimeException())
  }

  object rejectionsSetup {

    case class SampleRejection1(id: Int) extends Rejection

    abstract class AbstractSampleProblem(problemType: String, id: Int) extends Problem {
      override val `type` = s"http://sample.com/errors/$problemType"
      override val title = "Sample problem"
      val status = StatusCodes.BadRequest
      override val detail = s"sample problem with id = $id"
    }

    case class SampleProblem1(id: Int) extends AbstractSampleProblem("sample1", id)

    implicit def toProblem1(ex: SampleRejection1): Problem = SampleProblem1(ex.id)
  }

  import com.snapswap.problems.http.{handleRejections => handleCustomRejections, _}
  import akka.http.scaladsl.server.Directives.{complete => completeDirective}

  "exceptionHandler" should {
    "serialize exception to a 'problem detail' JSON" in {
      import problemsSetup._

      val exceptionHandler = handleOnly[SampleException1] orElse
        handleOnly[SampleException2] orElse
        handleException[ParentException] orElse
        handleAny()

      val route: Route = handleExceptions(exceptionHandler) {
        get {
          path("failure1") {
            completeDirective {
              throwSampleException1
            }
          } ~ path("failure2") {
            completeDirective {
              throwSampleException2
            }
          } ~ path("failure3") {
            completeDirective {
              throwChildException
            }
          } ~ path("failureDefault") {
            completeDirective {
              throwRuntimeException
            }
          }
        }
      }

      Get("/failure1") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            """{
              |  "type": "http://sample.com/errors/sample1",
              |  "title": "Sample problem",
              |  "status": 400,
              |  "detail": "sample problem with id = 1"
              |}""".stripMargin
      }

      Get("/failure2") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            """{
              |  "type": "http://sample.com/errors/sample2",
              |  "title": "Sample problem",
              |  "status": 400,
              |  "detail": "sample problem with id = 2"
              |}""".stripMargin
      }

      Get("/failure3") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            """{
              |  "type": "http://sample.com/errors/sample3",
              |  "title": "Sample problem",
              |  "status": 400,
              |  "detail": "sample problem with id = 3"
              |}""".stripMargin
      }

      Get("/failureDefault") ~> route ~> check {
        status shouldBe StatusCodes.InternalServerError
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            s"""{
              |  "type": "about:blank",
              |  "title": "${StatusCodes.InternalServerError.reason}",
              |  "status": ${StatusCodes.InternalServerError.intValue},
              |  "detail": "${StatusCodes.InternalServerError.defaultMessage}"
              |}""".stripMargin
      }
    }
  }

  "rejectionHandler" should {
    "serialize rejection to a 'problem detail' JSON" in {
      import rejectionsSetup._

      val rejectionHandler = handleCustomRejections(handleRejection[SampleRejection1])

      val route: Route = handleRejections(rejectionHandler) {
        get {
          path("rejection1") {
            reject(SampleRejection1(1))
          } ~
          path("validationRejection") {
            reject(ValidationRejection("Sample rejection details"))
          }
        }
      }

      Get("/rejection1") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            """{
              |  "type": "http://sample.com/errors/sample1",
              |  "title": "Sample problem",
              |  "status": 400,
              |  "detail": "sample problem with id = 1"
              |}""".stripMargin
      }

      Get("/validationRejection") ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType.mediaType shouldBe `application/problem+json`
        responseAs[String] shouldBe
            """{
              |  "type": "about:blank",
              |  "title": "Bad Request",
              |  "status": 400,
              |  "detail": "Sample rejection details"
              |}""".stripMargin
      }
    }
  }
}
