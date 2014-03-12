package scalasem.network.forwarding.dataplane

import scala.collection.mutable.HashMap

import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFFlowTable, OFRivuaiFlowTableEntry}
import scalasem.network.forwarding.controlplane.openflow.RivuaiControlPlane
import scalasem.network.forwarding.interface.OpenFlowPortManager
import scalasem.network.topology.{Link, Node}
import scalasem.network.traffic.GlobalFlowStore
import scalasem.util.XmlParser

class RivuaiDataPlane(node: Node) extends ResourceAllocator {

  private val alpha = XmlParser.getDouble("scalasim.rivuai.alpha", 0.5)
  private val controlPlane = node.controlplane.asInstanceOf[RivuaiControlPlane]
  private val interfaceManager = node.interfacesManager.asInstanceOf[OpenFlowPortManager]

  // port -> (jobid -> using bandwidth)
  private val jobidToCurrentRating = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> using bandwidth)
  private val jobidToVirtualCapacity = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> allocated bandwidth to each job)
  private val jobidToAllocation = new HashMap[Short, HashMap[Int, Double]]
  // port -> (jobid -> the flow number generated by the job in current switch)
  private val jobidToFlowNum = new HashMap[Short, HashMap[Int, Int]]

  override def reallocate(link: Link) {

  }

  override def allocate(link: Link) {

  }

  private[network] def regulateFlow() {
    // support only one flow table for now
    val flowTable = controlPlane.flowtables(0)

    //get the y_i on each port
    for (entry <- flowTable.entries.values) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val currentRate = GlobalFlowStore.getFlow(
        OFFlowTable.createMatchFieldFromOFMatch(rivuaiEntry.ofmatch)).rate
      val outportNum = rivuaiEntry.outportNum
      if (outportNum >= 0) {
        val jobBucket = jobidToCurrentRating.getOrElseUpdate(outportNum, new HashMap[Int, Double]())
        jobBucket.getOrElseUpdate(rivuaiEntry.jobid, 0.0)
        jobBucket(rivuaiEntry.jobid) += currentRate
        jobidToFlowNum.getOrElseUpdate(outportNum, new HashMap[Int, Int]).
          getOrElseUpdate(rivuaiEntry.jobid, 0)
        jobidToFlowNum(outportNum)(rivuaiEntry.jobid) += 1
        if (rivuaiEntry.reqtype == 0) {
          jobidToVirtualCapacity.getOrElseUpdate(outportNum, new HashMap[Int, Double])
          jobidToVirtualCapacity(outportNum) += rivuaiEntry.jobid -> rivuaiEntry.reqvalue
        }
      }
    }

    // get capacity for weighted fair share flows
    // port number -> allocation
    val assignedToWFS = new HashMap[Short, Double]
    for (allocToMGEntry <- jobidToVirtualCapacity) {
      val capacity  = interfaceManager.getLinkByPortNum(allocToMGEntry._1).bandwidth
      val allocToMGPerPort = jobidToVirtualCapacity(allocToMGEntry._1).values
      val allocSumToMGPerPort = allocToMGPerPort.reduceLeft(_ + _)
      assignedToWFS += allocToMGEntry._1 -> (capacity - allocSumToMGPerPort)
    }

    // get C_i for every job
    // 1. get the sum of the priorities of jobs without minimum guarantee
    var sum = 0
    for (entry <- flowTable.entries.values
         if entry.asInstanceOf[OFRivuaiFlowTableEntry].reqtype != 0) {
      sum += entry.asInstanceOf[OFRivuaiFlowTableEntry].reqvalue
    }
    // 2. get C_i
    for (entry <- flowTable.entries.values
         if entry.asInstanceOf[OFRivuaiFlowTableEntry].reqtype != 0) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val outportNum = rivuaiEntry.outportNum
      if (outportNum >= 0) {
        val jobBucket = jobidToVirtualCapacity.getOrElseUpdate(outportNum, new HashMap[Int, Double]())
        jobBucket.getOrElseUpdate(rivuaiEntry.jobid, 0.0)
        jobBucket += rivuaiEntry.jobid ->
          ((rivuaiEntry.reqvalue / sum) * assignedToWFS(outportNum))
      }
    }

    // allocate R_i to each job
    for (allocPerPort <- jobidToAllocation; allocPerJob <- allocPerPort._2) {
      val jobid = allocPerJob._1
      val oldAlloc = allocPerJob._2
      val newC_i = jobidToVirtualCapacity(allocPerPort._1)(jobid)
      val measuredSpeed = jobidToCurrentRating(allocPerPort._1)(jobid)
      val newAlloc = oldAlloc * (1 - alpha * ((measuredSpeed - newC_i) / newC_i))
      val jobAlloc = jobidToAllocation.getOrElseUpdate(allocPerPort._1, new HashMap[Int, Double])
      jobAlloc += jobid -> newAlloc
    }

    // allocate to each flow
    for (entry <- flowTable.entries.values) {
      val rivuaiEntry = entry.asInstanceOf[OFRivuaiFlowTableEntry]
      val outport = rivuaiEntry.outportNum
      rivuaiEntry.ratelimit = jobidToAllocation(outport)(rivuaiEntry.jobid) /
        jobidToFlowNum(outport)(rivuaiEntry.jobid)
    }
    reset()
  }

  def reset() {
    //jobidToAllocation.clear()
    jobidToCurrentRating.clear()
    jobidToFlowNum.clear()
    jobidToVirtualCapacity.clear()
  }
}
