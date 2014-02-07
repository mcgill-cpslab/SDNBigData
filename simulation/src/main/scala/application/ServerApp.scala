package application

import network.topology.HostContainer
import simengine.utils.Logging

abstract class ServerApp(protected val servers : HostContainer) extends Logging {
  def run()
  def reset()
}

object ServerApp {
  def apply(appName : String, servers : HostContainer)  = {
    appName match {
      case "PermuMatrixApp" => new PermuMatrixApp(servers)
      case "OnOffApp" => new OnOffApp(servers)
      case "MapReduce" => new MapReduceApp(servers)
    }
  }
}




