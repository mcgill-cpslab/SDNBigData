package scalasem.network.forwarding.controlplane.openflow.flowtable

import scala.collection.JavaConversions._

import org.openflow.protocol.{OFFlowMod1, OFFlowMod}

import scalasem.network.forwarding.controlplane.openflow.{RivuaiControlPlane, OpenFlowControlPlane}
import scalasem.simengine.SimulationEngine

class OFRivualFlowTable(tableid: Short, ofcontrolplane: RivuaiControlPlane)
  extends OFFlowTableBase(tableid, ofcontrolplane) {

  /**
   *
   * @param flow_mod
   * @return the updated entries
   */
  override def addFlowTableEntry(flow_mod : OFFlowMod) = {
    assert(flow_mod.getCommand == OFFlowMod.OFPFC_ADD)
    logDebug(ofcontrolplane.node.ip_addr(0) + " insert flow table entry with " + flow_mod.getMatch)
    val flow_mod_1 = flow_mod.asInstanceOf[OFFlowMod1]
    val newEntryValue = getNewEntry.asInstanceOf[OFRivuaiFlowTableEntry]
    newEntryValue.ofmatch = flow_mod.getMatch
    newEntryValue.priority = flow_mod.getPriority
    flow_mod.getActions.toList.foreach(f => newEntryValue.actions += f)
    //schedule matchfield entry clean event
    newEntryValue.flowHardExpireMoment =
      (SimulationEngine.currentTime + flow_mod.getHardTimeout).toInt
    newEntryValue.flowIdleDuration = flow_mod.getIdleTimeout
    newEntryValue.refreshlastAccessPoint()
    newEntryValue.jobid = flow_mod_1.getJobid
    newEntryValue.reqtype = flow_mod_1.getReqtype.toInt
    newEntryValue.reqvalue = flow_mod_1.getReqvalue
    entries += (OFFlowTableBase.createMatchFieldFromOFMatch(newEntryValue.ofmatch,
      newEntryValue.ofmatch.getWildcards) -> newEntryValue)
    entries
  }
}
