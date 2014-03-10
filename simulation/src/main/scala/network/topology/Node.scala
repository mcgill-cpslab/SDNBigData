package scalasem.network.topology

import scala.collection.mutable.ListBuffer

import scalasem.network.forwarding.dataplane.ResourceAllocator
import scalasem.network.forwarding.interface.InterfacesManager
import scalasem.network.forwarding.controlplane.RoutingProtocol

abstract class NodeType

case object AggregateRouterType extends NodeType
case object ToRRouterType extends  NodeType
case object CoreRouterType extends NodeType
case object HostType extends NodeType

class Node (val nodetype : NodeType,
  val globalDeviceId : Int) {

  def id_gen(pid : Int, swid: Int, hostid: Int): Long = {
    pod_id = pid
    sw_id = swid
    host_id = hostid
    dpid = (pod_id << 16) + (sw_id << 8) + host_id
    dpid
  }

  private var pod_id: Int = 0
  private var sw_id: Int = 0
  private var host_id: Int = 0
  private var dpid: Long = 0

  lazy val DPID = dpid

  val mac_addr : ListBuffer[String] = new ListBuffer[String]
  val ip_addr : ListBuffer[String] = new ListBuffer[String]

  val controlplane = RoutingProtocol(this)
  val dataplane =  ResourceAllocator(this)
  val interfacesManager = InterfacesManager(this)


  def assignIP(ip : String) {
    ip_addr += ip
  }

  def assignMac(mac : String) {
    mac_addr += mac
  }

  override def toString = {
    if (ip_addr.length > 0) ip_addr(0)
    else "new initialized node"
  }
}

trait NodeContainer {
  val nodecontainer = new ListBuffer[Node]()
  var idx: Int = 0;

  def create(nodeN : Int)

  def size() : Int = nodecontainer.size

  def apply(idx : Int) = nodecontainer.apply(idx)

  def addNode(node : Node) {nodecontainer += node}
}




