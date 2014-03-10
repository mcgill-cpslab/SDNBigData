package scalasem.network.forwarding.interface

import scalasem.network.topology.Node


/**
 * InterfacesManager manages the interfaces in the device connecting with others
 */
class DefaultInterfacesManager (node : Node) extends InterfacesManager {
  serveNode = node
}
