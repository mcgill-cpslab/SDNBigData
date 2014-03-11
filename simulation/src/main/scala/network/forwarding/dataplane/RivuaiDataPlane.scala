package network.forwarding.dataplane

import scalasem.network.forwarding.controlplane.openflow.RivuaiControlPlane
import scalasem.network.forwarding.dataplane.ResourceAllocator
import scalasem.network.topology.{Link, Node}

class RivuaiDataPlane(node: Node) extends ResourceAllocator {

  val controlPlane = node.controlplane.asInstanceOf[RivuaiControlPlane]

  override def reallocate(link: Link) {

  }

  override def allocate(link: Link) {

  }
}
