package scalasem.network.topology


class Host (hosttype : NodeType, globaldevId : Int) extends Node (hosttype, globaldevId)

class HostContainer extends NodeContainer {

  def create(nodeN : Int) {
    for (i <- 0 until nodeN) {
      nodecontainer += new Host(HostType, GlobalDeviceManager.globalDeviceCounter)
      GlobalDeviceManager.globalDeviceCounter += 1
    }
  }

  def addHost(servers : HostContainer) {
    for (i <- 0 until servers.size) nodecontainer += servers(i)
  }

  override def apply(i : Int) = nodecontainer(i).asInstanceOf[Host]
}
