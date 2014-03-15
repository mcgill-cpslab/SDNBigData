package scalasem.network.forwarding.controlplane.openflow

import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFRivuaiFlowTableEntry, OFFlowTable}
import scalasem.network.topology.{ToRRouterType, HostType, Router}
import scalasem.network.traffic.Flow
import scalasem.util.XmlParser

class RivuaiControlPlane(router: Router) extends OpenFlowControlPlane(router) {

  def getAllowedRate(flow: Flow): Double =  {
    if (router.nodeType == ToRRouterType) {
      val matchfield = OFFlowTable.createMatchField(flow)
      val entry = flowtables(0).queryTableByMatch(matchfield)
      if (entry.length > 0)
        entry.asInstanceOf[OFRivuaiFlowTableEntry].ratelimit
      else
        XmlParser.getDouble("scalasim.rivuai.baserate", 100.0)
    } else {
      Double.MaxValue
    }
  }
}