import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.Http
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

object Cluwa {
  def main(args: Array[String]) {
    val port = args(0).toInt

    // Override the configuration of the port
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load())

    // Create an Akka system
    implicit val system = ActorSystem("ClusterSystem", config)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    ClusterSharding(system).start(
      typeName = "Player",
      entityProps = Props[Player],
      settings = ClusterShardingSettings(system),
      extractEntityId = Player.idExtractor,
      extractShardId = Player.shardResolver)

    val httpService = new HttpService
    val restPort: Int = 10000 + port
    val bindingFuture = Http().bindAndHandle(httpService.routes, "localhost", restPort)

    println(s"Server online at http://localhost:" + restPort + "/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}