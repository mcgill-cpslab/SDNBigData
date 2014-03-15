package scalasem.network.traffic

import scala.collection.mutable.HashMap

import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTableBase
import scalasem.network.forwarding.controlplane.openflow.OFMatchField

object GlobalFlowStore {
  private val flowstore = new HashMap[OFMatchField, Flow]

  def addFlow(flow : Flow) {
    val matchfield = OFFlowTableBase.createMatchField(flow)
    flowstore += matchfield -> flow
  }

  def removeFlow(matchfield: OFMatchField) {
    flowstore -= matchfield
  }

  def getFlow(matchfield: OFMatchField) = flowstore(matchfield)

  def getFlows : List[Flow] = flowstore.values.toList

  def size = flowstore.size
}
