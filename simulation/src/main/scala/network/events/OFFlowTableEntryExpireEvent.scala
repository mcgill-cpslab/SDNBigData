package scalasem.network.events

import org.openflow.protocol.OFMatch

import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTableBase
import scalasem.network.forwarding.controlplane.openflow.OFMatchField
import scalasem.simengine.EventOfTwoEntities
import scalasem.util.Logging

final class OFFlowTableEntryExpireEvent (offlowTable : OFFlowTableBase,
                                         matchfield : OFMatchField, t : Double)
  extends EventOfTwoEntities[OFFlowTableBase, OFMatch] (offlowTable,
    matchfield, t) with Logging {

  def process {
    logInfo("entry for " + matchfield.toString + " expires at " + t)
    offlowTable.removeEntry(matchfield)
  }
}
