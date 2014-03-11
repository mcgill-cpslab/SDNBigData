package scalasem.network.forwarding.controlplane.openflow

import scala.collection.mutable.HashMap

import scalasem.network.topology.Router
import scalasem.network.traffic.Flow

class RivuaiControlPlane(node: Router) extends OpenFlowControlPlane(node) {


}

object RivuaiControlPlane {

  //global flow hashmap
  val globalFlowTable = new HashMap[OFMatchField, Flow]

}
