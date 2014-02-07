package network.events

import org.openflow.protocol.OFMatch
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import simengine.utils.Logging
import network.forwarding.controlplane.openflow.OFMatchField
import simengine.EventOfTwoEntities

final class OFFlowTableEntryExpireEvent (offlowTable : OFFlowTable,
                                         matchfield : OFMatchField, t : Double)
  extends EventOfTwoEntities[OFFlowTable, OFMatch] (offlowTable,
    matchfield, t) with Logging {

  def process {
    logInfo("entry for " + matchfield.toString + " expires at " + t)
    offlowTable.removeEntry(matchfield)
  }
}
