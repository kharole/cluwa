import Player.GetBalance
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.SecurityDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import scala.concurrent.{ExecutionContext, Future}

class HttpService(system: ActorSystem)(implicit executionContext: ExecutionContext) extends CirceSupport with SecurityDirectives {

  val playerRegion = ClusterSharding(system).shardRegion(Player.shardName)

  val routes =
    pathPrefix("v1") {
      path("balance") {
        pathEndOrSingleSlash {
          post {
            entity(as[BalanceRequest]) { req =>
              complete(handle(req).map(_.asJson))
            }
          }
        }
      }
    }

  def handle(req: BalanceRequest): Future[BalanceResponse] = {
    implicit val timeout = Timeout(5 seconds)
    (playerRegion ? GetBalance(req.playerName)).mapTo[Int].map(balance => BalanceResponse(balance))
  }

  case class BalanceRequest(playerName: String)

  case class BalanceResponse(balance: Int)

}
