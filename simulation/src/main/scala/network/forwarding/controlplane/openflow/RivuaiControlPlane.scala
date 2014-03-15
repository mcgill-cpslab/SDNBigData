package scalasem.network.forwarding.controlplane.openflow

import scalasem.network.events.RivuaiRateControlEvent
import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFRivuaiFlowTableEntry, OFFlowTable}
import scalasem.network.forwarding.dataplane.RivuaiDataPlane
import scalasem.network.topology.{ToRRouterType, HostType, Router}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser

class RivuaiControlPlane(router: Router) extends OpenFlowControlPlane(router) {

  def getAllowedRate(flow: Flow): Double =  {
    if (router.nodeType == ToRRouterType) {
      val matchfield = OFFlowTable.createMatchField(flow)
      // should always return top 1 entry
      val entry = flowtables(0).queryTableByMatch(matchfield)
      if (entry.length > 0) {
        entry.asInstanceOf[OFRivuaiFlowTableEntry].ratelimit
      }
      else {
        XmlParser.getDouble("scalasim.rivuai.baserate", 100.0)
      }
    } else {
      //not ingress/egress switch
      Double.MaxValue
    }
  }

  private def startRateController() {
    if (router.nodeType == ToRRouterType) {
      val startTime = SimulationEngine.startTime
      val endTime = SimulationEngine.endTime
      val frequency = XmlParser.getDouble("simengine.rivuai.frequency", 0.005)

      for (s <- startTime until endTime by frequency) {
        val rcEvent = new RivuaiRateControlEvent(
          node.dataplane.asInstanceOf[RivuaiDataPlane], s)
        SimulationEngine.addEvent(rcEvent)
      }
    }
  }

  startRateController()
}