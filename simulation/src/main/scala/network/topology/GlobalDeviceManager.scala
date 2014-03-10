package scalasem.network.topology

import scala.collection.mutable.HashMap

object GlobalDeviceManager {
  var globalDeviceCounter = 0

  private val globalNodeMap = new HashMap[String, Node]

  def getNode (ip: String) = {
    assert(globalNodeMap.contains(ip), ip + " hasn't been registered in GlobalDeviceManager")
    globalNodeMap(ip)
  }

  def addNewNode(ip: String, node: Node) {
    globalNodeMap += ip -> node
  }

  def getAllRouters = {
    globalNodeMap.values.filter(node => node.nodetype != HostType)
  }

  def getAllHosts = {
    globalNodeMap.values.filter(node => node.nodetype == HostType)
  }
}
