import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.SecurityDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

class HttpService()(implicit executionContext: ExecutionContext) extends CirceSupport with SecurityDirectives {

  val routes =
    pathPrefix("v1") {
      path("test") {
        pathEndOrSingleSlash {
          post {
            entity(as[Request]) { req =>
              complete(handle(req).map(_.asJson))
            }
          }
        }
      }
    }

  def handle(req: Request): Future[Response] = Future {
    Response("concat", req.in1 + req.in2)
  }

  case class Request(in1: String, in2: String)

  case class Response(out1: String, out2: String)

}
