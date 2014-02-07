package application

import scala.util.Random
import network.topology.{Host, HostContainer}
import network.traffic.Flow
import simengine.SimulationEngine
import network.events.StartNewFlowEvent
import scala.collection.immutable.HashMap

//build a permulate matrix between all machines,
//each machine should be selected for only once
//and do not allow to send to itself
class PermuMatrixApp (servers : HostContainer) extends ServerApp(servers) {
  private var selectedPair = new HashMap[Host, Host] //src ip -> dst ip

  private var selectedHost = List[Int]()

  private def selectMachinePairs(): Boolean = {
    for (i <- 0 until servers.size) {
      var proposedIdx = Random.nextInt(servers.size())
      while (selectedHost.contains(proposedIdx) ||
        proposedIdx == i) {
        proposedIdx = Random.nextInt(servers.size())
        if (i == servers.size - 1 && proposedIdx == i &&
          !selectedHost.contains(servers(i))) {
          //restart the selection
          reset()
          return false
        }
      }
      selectedHost = proposedIdx :: selectedHost
      selectedPair += servers(i) -> servers(proposedIdx)
    }
    true
  }

  def selectedPairSize = selectedPair.size

  def run() {
    while (!selectMachinePairs()){}
    for (srcdstPair <- selectedPair) {
      val newflowevent = new StartNewFlowEvent(
        Flow(srcdstPair._1.ip_addr(0), srcdstPair._2.ip_addr(0),
          srcdstPair._1.mac_addr(0), srcdstPair._2.mac_addr(0), appDataSize = 1),
        srcdstPair._1,
        SimulationEngine.currentTime)
      SimulationEngine.addEvent(newflowevent)
    }
  }

  def reset() {
    selectedPair = new HashMap[Host, Host]
    selectedHost = List()
  }
}


