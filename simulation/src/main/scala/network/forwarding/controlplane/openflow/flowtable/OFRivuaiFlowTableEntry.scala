package network.forwarding.controlplane.openflow.flowtable

import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFFlowTable, OFFlowTableEntryBase}

class OFRivuaiFlowTableEntry(table: OFFlowTable) extends OFFlowTableEntryBase(table) {
  private[openflow] var jobid: Int = -1
  private[openflow] var reqtype: Int = -1
  private[openflow] var reqvalue: Int = -1
  private[openflow] var ratelimit: Double = 0.0

}
