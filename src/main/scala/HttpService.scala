import Player.{Deposit, GetBalance, Rollback, Withdraw}
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
              complete(balance(req).map(_.asJson))
            }
          }
        }
      } ~
        path("deposit") {
          pathEndOrSingleSlash {
            post {
              entity(as[DepositRequest]) { req =>
                complete(deposit(req).map(_.asJson))
              }
            }
          }
        } ~
        path("withdraw") {
          pathEndOrSingleSlash {
            post {
              entity(as[WithdrawRequest]) { req =>
                complete(withdraw(req).map(_.asJson))
              }
            }
          }
        } ~
        path("rollback") {
          pathEndOrSingleSlash {
            post {
              entity(as[RollbackRequest]) { req =>
                complete(rollback(req).map(_.asJson))
              }
            }
          }
        }

    }

  case class BalanceRequest(playerName: String)

  case class BalanceResponse(balance: Int)

  def balance(req: BalanceRequest): Future[BalanceResponse] = {
    implicit val timeout = Timeout(5 seconds)
    (playerRegion ? GetBalance(req.playerName)).mapTo[Int].map(balance => BalanceResponse(balance))
  }


  case class DepositRequest(playerName: String, id: String, amount: Int)

  case class DepositResponse(balance: Int)

  def deposit(req: DepositRequest): Future[DepositResponse] = {
    implicit val timeout = Timeout(5 seconds)
    (playerRegion ? Deposit(req.playerName, req.id, req.amount)).mapTo[Int].map(balance => DepositResponse(balance))
  }

  case class WithdrawRequest(playerName: String, id: String, amount: Int)

  case class WithdrawResponse(balance: Int)

  def withdraw(req: WithdrawRequest): Future[WithdrawResponse] = {
    implicit val timeout = Timeout(5 seconds)
    (playerRegion ? Withdraw(req.playerName, req.id, req.amount)).mapTo[Int].map(balance => WithdrawResponse(balance))
  }

  case class RollbackRequest(playerName: String, id: String)

  case class RollbackResponse(balance: Int)

  def rollback(req: RollbackRequest): Future[RollbackResponse] = {
    implicit val timeout = Timeout(5 seconds)
    (playerRegion ? Rollback(req.playerName, req.id)).mapTo[Int].map(balance => RollbackResponse(balance))
  }
}
