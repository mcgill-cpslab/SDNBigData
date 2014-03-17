package scalasem.network.events

import scalasem.network.forwarding.dataplane.RivuaiDataPlane
import scalasem.network.topology.Node
import scalasem.simengine.EventOfSingleEntity

class RivuaiRateControlEvent(node: Node, ts: Double )
  extends EventOfSingleEntity[Node](node, ts) {

  override def process() {
    if (node.dataplane == null)
      println(node.ip_addr(0))
    node.dataplane.asInstanceOf[RivuaiDataPlane].regulateFlow()
  }
}
