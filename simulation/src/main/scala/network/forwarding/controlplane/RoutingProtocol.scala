package network.forwarding.controlplane

import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.collection.mutable
import org.openflow.protocol.OFMatch
import network.topology._
import network.traffic.Flow
import simengine.utils.{XmlParser, Logging}
import network.forwarding.controlplane.openflow.{OFMatchField, OpenFlowControlPlane}
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import utils.IPAddressConvertor
import simengine.SimulationEngine

/**
 *  the trait representing the functionalities to calculate
 *  the routing path locally or remotely
 */
trait RoutingProtocol extends Logging {

  protected val RIBIn = new mutable.HashMap[OFMatchField, Link]
    with mutable.SynchronizedMap[OFMatchField, Link]
  protected val RIBOut = new mutable.HashMap[OFMatchField, Link]
    with mutable.SynchronizedMap[OFMatchField, Link]

  protected var wildcard = ~OFMatch.OFPFW_ALL

  def fetchInRoutingEntry(ofmatch : OFMatch) : Link = {
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch)
    logDebug("quering matchfield: " + matchfield + "(" + matchfield.hashCode + ")" +
      " node:" + this)
    assert(RIBIn.contains(matchfield))
    RIBIn(matchfield)
  }

  def fetchOutRoutingEntry(ofmatch : OFMatch) : Link = {
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch)
    assert(RIBOut.contains(matchfield))
    RIBOut(matchfield)
  }

  def insertOutPath (ofmatch : OFMatch, link : Link)  {
    logTrace(this + " insert outRIB entry " +
      IPAddressConvertor.IntToDecimalString(ofmatch.getNetworkSource) + "->" +
      IPAddressConvertor.IntToDecimalString(ofmatch.getNetworkDestination) +
      " with the link " + link.toString)
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch = ofmatch)
    RIBOut += (matchfield -> link)

  }

  def insertInPath (ofmatch : OFMatch, link : Link) {
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch = ofmatch)
    RIBIn += (matchfield -> link)
    logTrace(this + " insert inRIB entry " + matchfield + "(" + matchfield.hashCode
      + ") -> " + link + " RIBIn Length:" + RIBIn.size)
  }

  def deleteEntry(ofmatch : OFMatch) {
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch)
    logTrace("delete entry:" + matchfield + " at node:" + this)
    RIBIn -= matchfield
    RIBOut -= matchfield
  }

  /**
   * route the flow to the next node
   * @param flow the flow to be routed
   * @param inlink it can be null (for the first hop)
   */
  def routing (localnode : Node, flow : Flow, matchfield : OFMatchField, inlink : Link) {
    //discard the flood packets
    if (wrongDistination(localnode, flow)) return
    logTrace("arrive at " + localnode.ip_addr(0) +
      ", routing (flow : Flow, matchfield : OFMatch, inlink : Link)" +
      " flow:" + flow + ", inlink:" + inlink)
    if (localnode.ip_addr(0) == flow.dstIP) {
      //start resource allocation process
      flow.setEgressLink(inlink)
      localnode.dataplane.allocate(localnode, flow, inlink)
    } else {
      if (!flow.floodflag) {
        val nextlink = selectNextHop(flow, matchfield, inlink)
        if (nextlink != null) {
          forward(localnode, nextlink, inlink, flow, matchfield)
        }
      } else {
        floodoutFlow(localnode, flow, matchfield, inlink)
      }
    }
  }

  /**
   *
   * @param flow
   * @param matchfield
   * @param inlink
   */
  def floodoutFlow(localnode: Node, flow : Flow, matchfield : OFMatchField, inlink : Link) {
    //remove duplicate flow (when flooding)
    if (!RIBIn.contains(matchfield)) {
      //it's a flood flow
      logTrace("flow " + flow + " is flooded out at " + localnode)
      val nextlinks = localnode.interfacesManager.getfloodLinks(localnode, inlink)
      //TODO : openflow flood handling in which nextlinks can be null?
      nextlinks.foreach(l => {
        flow.addTrace(l, inlink)
        Link.otherEnd(l, localnode).controlplane.routing(Link.otherEnd(l, localnode), flow, matchfield, l)
      })
      if (inlink != null) insertInPath(matchfield, inlink)
    } else {
      //TODO: should allow duplicate flow
      SimulationEngine.queueReadingLock.release()
    }
  }

  def forward (localnode: Node, olink : Link, inlink : Link, flow : Flow, matchfield : OFMatchField) {
    if (!RIBIn.contains(matchfield)) {
      val nextnode = Link.otherEnd(olink, localnode)
      logDebug("send through " + olink)
      flow.addTrace(olink, inlink)
      nextnode.controlplane.routing(nextnode, flow, matchfield, olink)
      if (inlink != null) insertInPath(matchfield, inlink)
    } else {
      //TODO: should allow duplicate flow
      SimulationEngine.queueReadingLock.release()
    }
  }


  private def wrongDistination(localnode: Node, flow : Flow) : Boolean = {
    if (localnode.ip_addr(0) !=  flow.srcIP && localnode.ip_addr(0) != flow.dstIP && localnode.nodetype == HostType) {
      logTrace("Discard flow " + flow + " on node " + localnode.toString)
      return true
    }
    false
  }

  //abstract methods
  def selectNextHop(flow : Flow, matchfield : OFMatchField, inPort : Link) : Link
}

object RoutingProtocol {

  def apply(node : Node) : RoutingProtocol = {
    XmlParser.getString("scalasim.simengine.model", "default") match {
      case "default" => new DefaultControlPlane(node)
      case "openflow" => {
        node.nodetype match {
          case HostType => new DefaultControlPlane(node)
          case _ => new OpenFlowControlPlane(node)
        }
      }
      case _ => null
    }
  }
}

