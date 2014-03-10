package scalasem.network.forwarding.interface

import scala.collection.mutable.HashMap

import scalasem.network.topology.{Link, HostType, Node}
import scalasem.util.{Logging, XmlParser}


trait InterfacesManager extends Logging {

  protected var serveNode: Node = null

  private [forwarding] val outlinks = new HashMap[String, Link] // key -> destination ip

  //if the other end is a host, then the key is the ip of the host,
  //if the other end is a router, then the key is the IP range of that host
  private [forwarding] val inlinks = new HashMap[String, Link]

  def getOutLinks (ip : String) = outlinks.get(ip)
  def getInLinks (ip : String) = inlinks.get(ip)

  def registerOutgoingLink(l : Link) {
    logDebug(serveNode.ip_addr(0) + " adds outlink:" + l)
    outlinks += (l.end_to.ip_addr(0) -> l)
  }

  def registerIncomeLink(l : Link) {
    logDebug(serveNode.ip_addr(0) + " adds inlink:" + l)
    inlinks += l.end_from.ip_addr(0) -> l
  }

  def getNeighbour(localnode : Node, l : Link) : Node = {
    assert(l.end_from == localnode || l.end_to == localnode)
    if (l.end_to == localnode) l.end_from
    else l.end_to
  }

  def getfloodLinks(localnode: Node, inport: Link): List[Link] = {
    logDebug("calculating floodlink in " + localnode)
    val alllink = {
      if (localnode.nodetype != HostType)
        inlinks.values.toList ::: outlinks.values.toList
      else outlinks.values.toList
    }
    alllink.filterNot(l => l == inport)
  }

}

object InterfacesManager {


  def apply(node : Node) = {
    XmlParser.getString("scalasim.simengine.model", "default") match {
      case "default" => new DefaultInterfacesManager(node)
      case "openflow" => node.nodetype match {
        case HostType => new DefaultInterfacesManager(node)
        case _ => new OpenFlowPortManager(node)
      }
      case _ => null
    }
  }
}
