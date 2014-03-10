package scalasem.network.component.builder

import scalasem.network.topology.{NodeContainer, Node}

object AddressInstaller {

  /**
   * assign IP to a single node
   * @param node the node to be assigned
   * @param ip ip address
   */
  def assignIPAddress(node : Node, ip : String) {
    node.assignIP(ip)
    //node.assignMac(HexString.toHexString(node.globalDeviceId, 6))
  }

  /**
   * assign MAC to a single node
   * @param node
   * @param macaddr
   */
  def assignMacAddress(node: Node, macaddr: String) {
    node.assignMac(macaddr)
  }

  /**
   * assign ip address to a set of ndoes
   * @param ipbase specifying the C range of the ip address
   * @param startAddress, the startaddress of the addresses to be assigned
   * @param nodes, the node container which containing the address
   * @param startIdx,start idx of hte node in the container
   * @param endIdx, the end of hte node in the container
   */
  def assignIPAddress (ipbase : String,
                       startAddress : Int,
                       nodes : NodeContainer,
                       startIdx : Int,
                       endIdx : Int) {
    val ip_prefix : String =  ipbase.substring(0, ipbase.lastIndexOf('.') + 1)
    for (i <- startIdx to endIdx) {
      nodes(i).assignIP(ip_prefix + (startAddress + i - startIdx).toString)
    }
  }
}
