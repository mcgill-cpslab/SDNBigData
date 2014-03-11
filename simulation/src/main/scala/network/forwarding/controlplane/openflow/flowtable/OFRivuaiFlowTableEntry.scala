package scalasem.network.forwarding.controlplane.openflow.flowtable

import scala.collection.mutable.ListBuffer

import org.openflow.protocol.OFMatch
import org.openflow.protocol.action.OFAction


class OFRivuaiFlowTableEntry(table: OFFlowTable) extends OFFlowTableEntryBase(table) {
  private[forwarding] var jobid: Int = -1
  private[forwarding] var reqtype: Int = -1
  private[forwarding] var reqvalue: Int = -1
  private[forwarding] var ratelimit: Double = 0.0

  override private[forwarding] var ofmatch: OFMatch = null
  override private[forwarding] val actions : ListBuffer[OFAction] = new ListBuffer[OFAction]

  // last timestamp when the flow is checked by Rivuai
  private[forwarding] var lastCheckpoint: Double = 0.0
}
