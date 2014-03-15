package scalasem.network.forwarding.dataplane

import scala.collection.mutable.HashMap

import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFFlowTableBase, OFRivuaiFlowTableEntry}
import scalasem.network.forwarding.controlplane.openflow.RivuaiControlPlane
import scalasem.network.forwarding.interface.OpenFlowPortManager
import scalasem.network.topology.{GlobalDeviceManager, HostType, Link, Node}
import scalasem.network.traffic._
import scalasem.util.XmlParser

class RivuaiDataPlane(node: Node) extends ResourceAllocator {

  private val alpha = XmlParser.getDouble("scalasim.rivuai.alpha", 0.5)
  private val controlPlane = node.controlplane.asInstanceOf[RivuaiControlPlane]
  private val interfaceManager = node.interfacesManager.asInstanceOf[OpenFlowPortManager]
  // support only one flow table for now
  private val flowTable = controlPlane.flowtables(0)


  // port -> (jobid -> using bandwidth)
  private val jobidToCurrentRating = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> using bandwidth)
  private val jobidToVirtualCapacity = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> allocated bandwidth to each job)
  private val jobidToAllocation = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> the flow number generated by the job in current switch)
  private val jobidToFlowNum = new HashMap[Short, HashMap[Int, Int]]

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

  /**
   * perform max min allocation on the link
   * for now, if the flow's rate is reduced, it does not trigger the allocation
   * in the path of other flows
   * @param link the input link
   */
  override def allocate(link: Link) {
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
      val allowedRate = controlPlane.getAllowedRate(currentflow)
      var demand = {
        if (currentflow.status != RunningFlow) math.min(currentflow.getTempRate, allowedRate)
        else math.min(currentflow.Rate, allowedRate)
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

  private def getInPortToHost(rivuaiEntry: OFRivuaiFlowTableEntry): Short =  {
    val inportNum = rivuaiEntry.inportNum
    val isIngressSwitch = inportNum >= 0 &&
      Link.otherEnd(interfaceManager.getLinkByPortNum(inportNum), node).nodeType == HostType
    if (isIngressSwitch) inportNum
    else -1
  }

  private def measureFlowRateOnEachPort() {
    // get the y_i on each port
    for (entry <- flowTable.entries.values) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val flow = GlobalFlowStore.getFlow(
        OFFlowTableBase.createMatchFieldFromOFMatch(rivuaiEntry.ofmatch))
      val currentRate = flow.totalSize - flow.remainingAppData
      val inPortNum = getInPortToHost(rivuaiEntry)
      if (inPortNum >= 0) {
        val jobBucket = jobidToCurrentRating.getOrElseUpdate(inPortNum,
          new HashMap[Int, Double]())
        jobBucket.getOrElseUpdate(rivuaiEntry.jobid, 0.0)
        jobBucket(rivuaiEntry.jobid) += currentRate
        if (rivuaiEntry.reqtype != 0) {
          //WFS flow
          jobidToFlowNum.getOrElseUpdate(inPortNum, new HashMap[Int, Int]).
            getOrElseUpdate(rivuaiEntry.jobid, 0)
          jobidToFlowNum(inPortNum)(rivuaiEntry.jobid) += 1
        }
        // save jobid -> vc for minimum guaranteed flows
        if (rivuaiEntry.reqtype == 0) {
          jobidToVirtualCapacity.getOrElseUpdate(inPortNum, new HashMap[Int, Double])
          jobidToVirtualCapacity(inPortNum) += rivuaiEntry.jobid -> rivuaiEntry.reqvalue
        }
      }
    }
  }

  private def calculateCapacityForEachFlow() {
    // get capacity for weighted fair share flows on each port
    // port number -> allocation
    val totalCapacityToWFSFlow = new HashMap[Short, Double]
    for (allocToMGEntry <- jobidToVirtualCapacity) {
      val capacity  = interfaceManager.getLinkByPortNum(allocToMGEntry._1).bandwidth
      //FIFO for MG flows
      var totalMGRate = 0.0
      for (entry <- jobidToVirtualCapacity(allocToMGEntry._1).values
           if entry.asInstanceOf[OFRivuaiFlowTableEntry].reqtype == 0){
        val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
        if (totalMGRate + rivuaiEntry.reqvalue <= capacity)
          totalMGRate += rivuaiEntry.reqvalue
        else {
          //if cannot meet the requirement, then make the flow with 0 allowed rate
          rivuaiEntry.ratelimit = 0
        }
      }
      totalCapacityToWFSFlow += allocToMGEntry._1 -> (capacity - totalMGRate)
    }

    // get C_i for every job
    // 1. get the sum of the priorities of jobs without minimum guarantee
    //  on each port
    val sumPerPort = new HashMap[Short, Double]
    for (entry <- flowTable.entries.values
         if entry.asInstanceOf[OFRivuaiFlowTableEntry].reqtype != 0) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      sumPerPort.getOrElseUpdate(rivuaiEntry.inportNum, 0)
      sumPerPort(rivuaiEntry.inportNum) += rivuaiEntry.reqvalue
    }
    // 2. get C_i for WFS flows
    for (entry <- flowTable.entries.values
         if entry.asInstanceOf[OFRivuaiFlowTableEntry].reqtype != 0) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val inPortNum = rivuaiEntry.inportNum
      if (inPortNum >= 0) {
        val jobBucket = jobidToVirtualCapacity.getOrElseUpdate(inPortNum,
          new HashMap[Int, Double]())
        jobBucket.getOrElseUpdate(rivuaiEntry.jobid, 0.0)
        jobBucket += rivuaiEntry.jobid ->
          ((rivuaiEntry.reqvalue / sumPerPort(inPortNum)) * totalCapacityToWFSFlow(inPortNum))
      }
    }
  }

  private def allocateToEachFlow() {
    // allocate R_i to each job
    for (allocPerPort <- jobidToAllocation; allocPerJob <- allocPerPort._2) {
      val jobid = allocPerJob._1
      val oldAlloc = allocPerJob._2
      val newJobCapacity = jobidToVirtualCapacity(allocPerPort._1)(jobid)
      val measuredSpeed = jobidToCurrentRating(allocPerPort._1)(jobid)
      val newAlloc = oldAlloc * (1 - alpha * ((measuredSpeed - newJobCapacity) / newJobCapacity))
      val jobAlloc = jobidToAllocation.getOrElseUpdate(allocPerPort._1, new HashMap[Int, Double])
      jobAlloc += jobid -> newAlloc
    }
    // allocate to each flow
    for (entry <- flowTable.entries.values) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val inport = rivuaiEntry.inportNum
      rivuaiEntry.ratelimit = jobidToAllocation(inport)(rivuaiEntry.jobid) /
        jobidToFlowNum(inport)(rivuaiEntry.jobid)
    }
  }

  private[network] def regulateFlow() {
    measureFlowRateOnEachPort()
    calculateCapacityForEachFlow()
    allocateToEachFlow()
    reset()
  }

  def reset() {
    jobidToCurrentRating.clear()
    jobidToFlowNum.clear()
    jobidToVirtualCapacity.clear()
  }
}
