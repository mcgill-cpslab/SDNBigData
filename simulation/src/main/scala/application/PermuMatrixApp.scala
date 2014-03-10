package scalasem.application

import scala.util.Random
import scala.collection.immutable.HashMap

import scalasem.network.topology.{Host, HostContainer}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.network.events.StartNewFlowEvent

//build a permulate matrix between all machines,
//each machine should be selected for only once
//and do not allow to send to itself
class PermuMatrixApp (servers : HostContainer) extends ServerApp(servers) {
  private var selectedPair = new HashMap[Host, Host]

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


