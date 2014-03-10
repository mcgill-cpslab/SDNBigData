package scalasem.network.forwarding.dataplane

import scalasem.network.topology._
import scalasem.network.traffic.{RunningFlow, ChangingRateFlow, FlowRateOrdering}
import scalasem.util.Logging


/**
 * the class representing the default functionalities to forward
 * packets/flows as well as the congestion control, etc.
 * that is the maxmin allocation
 */
class DefaultDataPlane extends ResourceAllocator with Logging {

  /**
   * perform max min allocation on the link
   * for now, if the flow's rate is reduced, it does not trigger the allocation
   * in the path of other flows
   * @param link the input link
   */
  override def allocate (link: Link) {
    if (linkFlowMap(link).size == 0) return
    var demandingflows = linkFlowMap(link).clone()
    var remainingBandwidth = link.bandwidth
    var avrRate = link.bandwidth / linkFlowMap(link).size
    logDebug("avrRate on " + link + " is " + avrRate)
    demandingflows = demandingflows.sorted(FlowRateOrdering)
    while (demandingflows.size != 0 && remainingBandwidth != 0) {
      val currentflow = demandingflows.head
      //initialize for the new flow
      if (currentflow.getTempRate == Double.MaxValue)
        currentflow.setTempRate(link.bandwidth)
      //for paused flow, we keep the running Status but set its rate to 0
      //because if we remove it from the flow list, we may need to recalculate the
      //path for it
      var demand = {
        if (currentflow.status != RunningFlow) currentflow.getTempRate
        else currentflow.Rate
      }
      logDebug("rate demand of flow " + currentflow + " is " + demand + ", status:" +
        currentflow.status)
      if (demand <= avrRate) {
        remainingBandwidth -= demand
      } else {
        if (currentflow.status == RunningFlow) {
          //TODO: if avrRate < currentflow.rate trigger the change on its path
          currentflow.changeRate(avrRate)
          logDebug("change flow " + currentflow + " rate to " + currentflow.Rate)
        } else {
          currentflow.setTempRate(avrRate) //set to avrRate
          logDebug("change flow " + currentflow + " temprate to " + currentflow.getTempRate)
        }
        remainingBandwidth -= avrRate
      }
      demandingflows.remove(0)
      if (demandingflows.size != 0) avrRate = remainingBandwidth / demandingflows.size
    }
  }

  /**
   * reallocate the flows' rate on the link, always called when a flow
   * is finished or off
   * @param link on the involved link
   */
  override def reallocate(link: Link) {
    if (linkFlowMap(link).size == 0) return
    var remainingBandwidth = link.bandwidth
    //TODO: fix ChangingRateFlow bug
    val sensingflows = linkFlowMap(link).filter(f => f.Rate != 0)
    var demandingflows = sensingflows.clone()
    var avrRate = link.bandwidth / sensingflows.size
    logDebug("avrRate on " + link + " is " + avrRate)
    demandingflows.map(f => {f.status = ChangingRateFlow; f.setTempRate(avrRate)})
    demandingflows = demandingflows.sorted(FlowRateOrdering)
    while (demandingflows.size != 0 && remainingBandwidth != 0) {
      //the flow with the minimum rate
      val currentflow = demandingflows.head
      val flowdest = GlobalDeviceManager.getNode(currentflow.dstIP)
      //try to acquire the max-min rate starting from the dest of this flow, the following function
      //recursively calls itself
      flowdest.dataplane.allocate(flowdest, currentflow, currentflow.getEgressLink)
      demandingflows.remove(0)
      if (demandingflows.size != 0) avrRate = remainingBandwidth / demandingflows.size
    }
  }
}


