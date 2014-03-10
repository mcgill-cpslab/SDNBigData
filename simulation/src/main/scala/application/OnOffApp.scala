package scalasem.application

import scala.util.Random
import scala.collection.immutable.HashMap

import scalasem.network.topology.{Host, HostContainer}
import scalasem.network.events.{FlowOffEvent, StartNewFlowEvent}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser


class OnOffApp (servers : HostContainer) extends ServerApp(servers) {
  private var selectedPair = new HashMap[Host, Host]

  private var selectedHost = List[Int]()

  private def selectMachinePairs() {
    for (i <- 0 until servers.size) {
      var proposedIdx = Random.nextInt(servers.size())
      while (selectedHost.contains(proposedIdx) ||
        proposedIdx == i) {
        proposedIdx = Random.nextInt(servers.size())
        println("selecting new servers")
      }
      selectedHost = proposedIdx :: selectedHost
      selectedPair += servers(i) -> servers(proposedIdx)
      logInfo("selecting " + i + " with " + proposedIdx)
    }
  }


  def run() {
    if (selectedPair.size == 0) selectMachinePairs()
    for (srcdstPair <- selectedPair) {
      val flow = Flow(srcdstPair._1.ip_addr(0), srcdstPair._2.ip_addr(0),
        srcdstPair._1.mac_addr(0), srcdstPair._2.mac_addr(0),
        appDataSize = XmlParser.getDouble("scalasim.app.appsize", 100))
      val newflowevent = new StartNewFlowEvent(
        flow,
        srcdstPair._1,
        SimulationEngine.currentTime)
      SimulationEngine.addEvent(newflowevent)
      //start a off
      SimulationEngine.addEvent(new FlowOffEvent(flow,
        SimulationEngine.currentTime + Random.nextInt(OnOffApp.offLength)))
    }
  }

  def reset() {
    selectedPair = new HashMap[Host, Host]
  }
}

object OnOffApp {
  val onLength = XmlParser.getInt("scalasim.app.onoff.onlength", 10)
  val offLength = XmlParser.getInt("scalasim.app.onoff.offlength", 5)
}
