package scalasem.network.events

import org.openflow.protocol.OFMatch

import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.network.forwarding.controlplane.openflow.OFMatchField
import scalasem.simengine.EventOfTwoEntities
import scalasem.util.Logging

final class OFFlowTableEntryExpireEvent (offlowTable : OFFlowTable,
                                         matchfield : OFMatchField, t : Double)
  extends EventOfTwoEntities[OFFlowTable, OFMatch] (offlowTable,
    matchfield, t) with Logging {

  def process {
    logInfo("entry for " + matchfield.toString + " expires at " + t)
    offlowTable.removeEntry(matchfield)
  }
}
