package scalasem.network.forwarding.dataplane

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import org.openflow.protocol.OFMatch

import scalasem.network.topology.{Node, Link}
import scalasem.network.traffic._
import scalasem.util.{Logging, XmlParser}
import scalasem.network.events.CompleteFlowEvent
import scalasem.simengine.SimulationEngine

/**
 * class representing the functionalities on the bandwidth allocation among flows
 *
 */
trait ResourceAllocator extends Logging {

  /**
   * the data structure recording the flows on certain links
   */
  protected val linkFlowMap = new mutable.HashMap[Link, ListBuffer[Flow]]

  def insertNewLinkFlowPair(link : Link, flow : Flow) {
    linkFlowMap.getOrElseUpdate(link, new ListBuffer[Flow]) += flow
  }

  def getLinkAvailableBandwidth(l : Link) : Double = {
    var usedBandwidth = 0.0
    for (f <- linkFlowMap(l)) usedBandwidth += f.Rate
    //for double precision problem
    Math.max(l.bandwidth - usedBandwidth, 0)
  }

  def deleteFlow(flow : Flow) {
    linkFlowMap.find(linkflowpair => linkflowpair._2.contains(flow)) match {
      case Some(lfpair) => lfpair._2 -= flow
      case None => {}
    }
  }

  def apply(link : Link) : ListBuffer[Flow] = linkFlowMap(link)

  /**
   * allocate bandwidth for the flow from the flow's destination to the source of the flow
   * @param localnode the local node
   * @param flow the flow to be allocated
   * @param startinglink the starting point of the allocation
   */
  def allocate (localnode: Node, flow: Flow, startinglink: Link) {
    if (localnode.ip_addr(0) == flow.srcIP) {
      startFlow(flow)
    } else {
      logDebug("localnode:" + localnode)
      val laststep = {
        if (localnode.ip_addr(0) == flow.dstIP) startinglink
        else flow.getLastHop(startinglink)
      }
      val nextnode = Link.otherEnd(laststep, localnode)
      logTrace("allocate for flow " + flow.toString() +
        " on " + laststep + " at node " + localnode)
      allocateOnCurrentHop(localnode, flow, laststep)
      //continue the allocate process on the last hop
      nextnode.dataplane.allocate(nextnode, flow, laststep)
    }
  }

  /**
   * allocate for the flows on the link
   * @param localnode the local node
   * @param changingflow the new flow
   * @param link the involved link
   */
  private def allocateOnCurrentHop(localnode: Node, changingflow : Flow, link : Link) {
    val otherEnd = Link.otherEnd(link, localnode)
    val incalldataplane = {
      if (link.end_from == localnode) localnode.dataplane
      else otherEnd.dataplane
    }
    //if it's a new flow, first insert it into the newlinkflowpair
    if (changingflow.status == NewStartFlow) {
      incalldataplane.insertNewLinkFlowPair(link, changingflow)
    }
    //decide all flows' rate in the link
    incalldataplane.allocate(link)
  }

  private def startFlow (flow : Flow) {
    if (flow.status == NewStartFlow) {
      val completeEvent = new CompleteFlowEvent(
        flow,
        SimulationEngine.currentTime + flow.AppDataSize / flow.getTempRate)
      logTrace("schedule complete event " + completeEvent + " for " + flow + " at " +
        (SimulationEngine.currentTime + flow.AppDataSize / flow.getTempRate))
      SimulationEngine.addEvent(completeEvent)
      flow.bindEvent(completeEvent)
      //has found the destination, change the property
      if (flow.floodflag) flow.floodflag = false
    }
    flow.run()
    SimulationEngine.queueReadingLock.release()
    logDebug("release lock at DataPlane")
  }

  /**
   * release the resources occupied by the flow from the destination to the source
   * @param localnode the current hop of the flow
   * @param flow the ending flow
   * @param ofmatch matchfield of flow
   * @param startinglink can be null for the destination of the flow
   */
  def finishFlow(localnode: Node, flow : Flow, ofmatch : OFMatch, startinglink : Link = null) {
    if (localnode.ip_addr(0) != flow.srcIP) {
      logTrace("flow ended at " + localnode.ip_addr(0))
      val nextlink = {
        if (startinglink == null) flow.getEgressLink
        else flow.getLastHop(startinglink)
      }
      logTrace("get flow " + flow + "'s trace as " + nextlink)
      val nextnode = Link.otherEnd(nextlink, localnode)
      val node = {
        if (nextlink.end_from == localnode) localnode
        else nextnode
      }
      node.dataplane.deleteFlow(flow)
      //recursively to call the function
      nextnode.dataplane.finishFlow(nextnode, flow, ofmatch, nextlink)
      //reallocate resource to other flows
      logTrace("reallocate resource on " + localnode.toString)
      node.dataplane.reallocate(nextlink)
      localnode.controlplane.deleteEntry(ofmatch)
    }
  }

  def reallocate(localnode : Node, flow: Flow, ofmatch : OFMatch, startinglink : Link = null) {
    if (localnode.ip_addr(0) != flow.srcIP) {
      val nextlink = {
        if (startinglink == null) flow.getEgressLink
        else flow.getLastHop(startinglink)
      }
      val nextnode = Link.otherEnd(nextlink, localnode)
      val node = {
        if (nextlink.end_from == localnode) localnode
        else nextnode
      }
      //recursively to call the function
      nextnode.dataplane.reallocate(nextnode, flow, ofmatch, nextlink)
      //reallocate resource to other flows
      logTrace("reallocate resource on " + localnode.toString)
      node.dataplane.reallocate(nextlink)
    }
  }

  def allocate(link: Link)

  def reallocate(link: Link)
}


object ResourceAllocator {

  def apply(node : Node) : ResourceAllocator = {
    XmlParser.getString("scalasim.simengine.model", "default") match {
      case "default" => new DefaultDataPlane
      case "openflow" => new DefaultDataPlane
      case _ => null
    }
  }
}