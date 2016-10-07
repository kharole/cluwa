import Player.{Deposit, GetBalance, Withdraw}
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.PersistentActor

import scala.concurrent.duration._

object Player {

  def props(): Props = Props(new Player)

  case class GetBalance(playerName: String)

  case class Deposit(playerName: String, id: String, amount: Int)

  case class Withdraw(playerName: String, id: String, amount: Int)

  case class Rollback(playerName: String, id: String, amount: Int)

  val idExtractor: ShardRegion.ExtractEntityId = {
    case gb: GetBalance => (gb.playerName, gb)
    case d: GetBalance => (d.playerName, d)
    case w: GetBalance => (w.playerName, w)
    case r: Rollback => (r.playerName, r)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case gb: GetBalance => (gb.playerName.hashCode % 100).toString
    case d: GetBalance => (d.playerName.hashCode % 100).toString
    case w: GetBalance => (w.playerName.hashCode % 100).toString
    case r: Rollback => (r.playerName.hashCode % 100).toString
  }
}

final case class BalanceChanged(id: String, amount: Int)

class Player extends PersistentActor with ActorLogging {

  import ShardRegion.Passivate

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  override def persistenceId: String = "Player-" + self.path.name

  var balance = 123
  val transactions = collection.mutable.Set[String]()

  def updateState(event: BalanceChanged): Unit = {
    if (!transactions.contains(event.id)) {
      balance += event.amount
      transactions += event.id
    }
  }

  override def receiveRecover: Receive = {
    case evt: BalanceChanged => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case Deposit(playerName, id, amount) => persist(BalanceChanged(id, +amount))(updateState)
    case Withdraw(playerName, id, amount) => persist(BalanceChanged(id, -amount))(updateState)
    case GetBalance(_) => sender() ! balance
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop => context.stop(self)
  }
}
