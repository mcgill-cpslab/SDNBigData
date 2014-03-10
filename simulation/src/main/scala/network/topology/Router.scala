package scalasem.network.topology

import scalasem.network.forwarding.controlplane.openflow.OpenFlowControlPlane

class Router (nodetype : NodeType, globaldevid : Int)
  extends Node(nodetype, globaldevid) {

  private var rid : Int = 0

  def connectTOController() {
    if (controlplane.isInstanceOf[OpenFlowControlPlane]) {
      controlplane.asInstanceOf[OpenFlowControlPlane].connectToController()
    }
  }

  def disconnectFromController() {
    if (controlplane.isInstanceOf[OpenFlowControlPlane])
      controlplane.asInstanceOf[OpenFlowControlPlane].disconnectFormController()
  }

  def setrid (r : Int) { rid = r }
  def getrid = rid

}

class RouterContainer () extends NodeContainer {
  def create(nodeN : Int, rtype : NodeType) {
    for (i <- 0 until nodeN) {
      nodecontainer += new Router(rtype, GlobalDeviceManager.globalDeviceCounter)
      GlobalDeviceManager.globalDeviceCounter += 1
      this(i).setrid(i)
    }
  }

  def create(nodeN : Int) {
    throw new RuntimeException("you have to specify the router type")
  }

  override def apply(i : Int) = nodecontainer(i).asInstanceOf[Router]
}

