import Player.{Deposit, GetBalance, Rollback, Withdraw}
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

  case class Rollback(playerName: String, id: String)

  val idExtractor: ShardRegion.ExtractEntityId = {
    case gb: GetBalance => (gb.playerName, gb)
    case d: Deposit => (d.playerName, d)
    case w: Withdraw => (w.playerName, w)
    case r: Rollback => (r.playerName, r)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case gb: GetBalance => (gb.playerName.hashCode % 100).toString
    case d: Deposit => (d.playerName.hashCode % 100).toString
    case w: Withdraw => (w.playerName.hashCode % 100).toString
    case r: Rollback => (r.playerName.hashCode % 100).toString
  }

  val shardName = "Player"

}

final case class BalanceChanged(id: String, amount: Int)

final case class RolledBack(id: String)

class Player extends PersistentActor with ActorLogging {

  import ShardRegion.Passivate

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  override def persistenceId: String = "Player-" + self.path.name

  var balance = 0
  val transactions = collection.mutable.Map[String, Int]()
  val rolledback = collection.mutable.Set[String]()

  def processTx(event: BalanceChanged): Unit = {
    if (!transactions.contains(event.id)) {
      if (!rolledback.contains(event.id))
        balance += event.amount
      transactions.put(event.id, event.amount)
    }
    sender() ! balance
  }

  def cancelTx(event: RolledBack): Unit = {
    if (!rolledback.contains(event.id) &&
      transactions.contains(event.id)) {
      balance -= transactions(event.id)
    }
    rolledback += event.id
    sender() ! balance
  }

  override def receiveRecover: Receive = {
    case evt: BalanceChanged => processTx(evt)
  }

  override def receiveCommand: Receive = {
    case Deposit(_, id, amount) => persist(BalanceChanged(id, +amount))(processTx)
    case Withdraw(_, id, amount) => persist(BalanceChanged(id, -amount))(processTx)
    case Rollback(_, id) => persist(RolledBack(id))(cancelTx)
    case GetBalance(_) => sender() ! balance
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop => context.stop(self)
  }
}
