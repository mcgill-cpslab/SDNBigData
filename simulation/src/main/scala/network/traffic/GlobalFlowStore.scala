package scalasem.network.traffic

import scala.collection.mutable.HashSet

object GlobalFlowStore {
  private val flowstore = new HashSet[Flow]

  def addFlow(flow : Flow) {
    flowstore += flow
  }

  def removeFlow(flow : Flow) {
    flowstore -= flow
  }

  def getFlows : List[Flow] = flowstore.toList

  def size = flowstore.size
}
