package scalasem.network.traffic

import scala.collection.mutable.ListBuffer

import scalasem.network.topology.{HostType, Node, Link}
import scalasem.network.events.CompleteFlowEvent
import scalasem.network.forwarding.controlplane.openflow.OpenFlowControlPlane
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.network.forwarding.interface.OpenFlowPortManager
import scalasem.simengine.SimulationEngine
import scalasem.util.{XmlParser, Logging}

/**
 *
 * @param srcIP
 * @param dstIP
 * @param srcMac
 * @param dstMac
 * @param vlanID
 * @param prioritycode
 * @param remainingAppData
 */
class Flow (
  private [network] val srcIP : String,
  private [network] val dstIP : String,
  private [network] val srcMac : String,
  private [network] val dstMac : String,
  private [network] val vlanID : Short = 0xffff.asInstanceOf[Short],
  private [network] val prioritycode: Byte = 0,
  private [network] val srcPort : Short = 0,
  private [network] val dstPort : Short = 0,//set to 0 to wildcarding src/dst ports
  private var remainingAppData : Double,//in MB
  private [network] var floodflag : Boolean = false
  //to indicate this flow may be routed to the non-destination host
  ) extends Logging {

  var status : FlowStatus = NewStartFlow
  private var hop : Int = 0
  private var bindedCompleteEvent : CompleteFlowEvent = null
  private var lastChangePoint  = 0.0
  private var egressLink : Link = null

  //this value is dynamic,
  //mainly used by the openflow protocol to match flowtable
  private [network] var inport : Short = 0

  /**
   * track the flow's hops,
   * used to allocate resource in reverse order
   */
  private var trace_laststeptrack = Vector[(Link, Int)]()   //(link, lastlinkindex)
  private var trace = Vector[Link]()

  def DstIP = dstIP
  def SrcIP = srcIP

  private var rate : Double = 0.0
  private var tempRate : Double = Double.MaxValue

  def bindEvent(ce : CompleteFlowEvent) {
    logDebug("bind completeEvent for " + this)
    bindedCompleteEvent = ce
  }

  def hasBindedCompleteEvent = bindedCompleteEvent != null

  private def updateCounters(additionalPacket : Long, additionalBytes : Long,
                                      additionalDuration : Int) {

    def updateFlowCounter(node : Node) {
      if (node.nodetype != HostType) {
        val oftables = node.controlplane.asInstanceOf[OpenFlowControlPlane].FlowTables
        oftables.foreach(table => {
          val entries = table.matchFlow(OFFlowTable.createMatchField(this))
          if (entries != null) {
            //increase table counter
            // for flow simulation, we only set 0 to packet counters
            table.TableCounter.increaseTableCounters(0, 0)
            //increase the flow counter
            entries.foreach(entry => entry.Counters.increaseFlowCounters(additionalBytes,
              additionalPacket,
              additionalDuration,
              additionalDuration * 1000000000))
          }
        })
      }
    }

    def updatePortCounters(link : Link, linkIdx : Int) {
      //linkIdx > hop / 2 because we traverse all links in reverse order
      if (linkIdx > hop / 2) {
        if (link.end_from.nodetype != HostType) {
          val portManager = link.end_from.interfacesManager.asInstanceOf[OpenFlowPortManager]
          val portnumber = portManager.getPortByLink(link).getPortNumber
          val portcounter = portManager.getPortCounter(portnumber)
          portcounter.increasePortCounters(0, 0, 0, additionalBytes,
            0, 0, 0, 0, 0 ,0, 0, 0, 0)
        }
      } else {
        val portManager = link.end_to.interfacesManager.asInstanceOf[OpenFlowPortManager]
        val portnumber = portManager.getPortByLink(link).getPortNumber
        val portcounter = portManager.getPortCounter(portnumber)
        portcounter.increasePortCounters(0, 0, additionalBytes, 0,
          0, 0, 0, 0, 0 ,0, 0, 0, 0)
      }
    }

    if (XmlParser.getString("scalasim.simengine.model", "tcp") == "openflow") {
      //TODO:to be finished
      val checkedNode = new ListBuffer[Node]
      var link = egressLink
      var i = 0

      while (link != null) {
        if (!checkedNode.contains(link.end_from)) {
          updateFlowCounter(link.end_from)
          checkedNode += link.end_from
        }
        if (!checkedNode.contains(link.end_to)) {
          updateFlowCounter(link.end_to)
          checkedNode += link.end_to
        }
        updatePortCounters(link, i)
        //the following statement is correct, because trace_laststeptrack and trace
        // are always operated at the same time
        val lastindex = trace_laststeptrack(trace.indexOf(link))._2
        if (lastindex != -1)
          link = trace(lastindex)
        else
          link = null
        i += 1
      }
    }
  }

  /**
   * does not change the rate, just update
   * counters and remianingAppData
   */
  def updateTransferredData() {
    if (remainingAppData > 0) {
      remainingAppData -= rate * (SimulationEngine.currentTime - lastChangePoint)
      updateCounters(0,
        (rate * (SimulationEngine.currentTime - lastChangePoint)).asInstanceOf[Long],
        (SimulationEngine.currentTime - lastChangePoint).asInstanceOf[Int])
      lastChangePoint = SimulationEngine.currentTime
    }
  }

  /**
   * change the rate and update the counters, remaininingAppData
   * @param newRateValue new rate value
   */
  def changeRate(newRateValue : Double) {
    logDebug("old rate : " + rate + " new rate : " + newRateValue +
      ", lastChangePoint = " + lastChangePoint +
      " remainingAppData: " + remainingAppData)
    updateTransferredData()
    logTrace("change " + this + "'s lastChangePoint to " + SimulationEngine.currentTime +
      " remainingAppData: " + remainingAppData)
    rate = newRateValue
    if (rate == 0 && remainingAppData != 0) cancelBindedEvent()
    else {
      //TODO: may causing some duplicated rescheduling in successive links
      if ((status == RunningFlow || status == ChangingRateFlow) && remainingAppData > 0)
        rescheduleBindedEvent()
    }
  }

  def setTempRate(tr: Double) {tempRate = tr}

  def AppDataSize = remainingAppData

  def getTempRate = tempRate

  def Rate = rate

  /**
   * add the link to the flow's trace
   * @param newlink, the link to be added
   * @param lastlink, we need to know this parameter to track the last link of newlink
   */
  def addTrace(newlink: Link, lastlink : Link): Boolean =  {
    var lastlinkindex = -1
    if (lastlink != null) lastlinkindex = trace.indexOf(lastlink)
    //if (lastlink != null && lastlinkindex == -1) return false
    logDebug("add trace, currentlink:" + newlink + ", lastlink:" + lastlink +
      ", flow:" + this.toString + ", lastlinkindex:" + lastlinkindex)
    this.synchronized {
      trace_laststeptrack = trace_laststeptrack :+ (newlink, lastlinkindex)
      trace = trace :+ newlink
    }
    true
  }

  def getLastHop(curlink: Link) : Link = {
    logDebug("get the link " + curlink + "'s last step, flow:" + this.toString)
    val laststepindex = trace_laststeptrack(trace.indexOf(curlink))._2
    if (laststepindex < 0) {
      logDebug("last link index is smaller than 0, currentlink:" + curlink)
      return null
    }
    trace(laststepindex)
  }

  private def cancelBindedEvent() {
    if (bindedCompleteEvent != null) {
      logTrace("cancel " + toString + " completeEvent " +
        " current time:" + SimulationEngine.currentTime)
      SimulationEngine.cancelEvent(bindedCompleteEvent)
    }
  }

  //TODO: shall I move this method to the control plane or simulationEngine?
  private def rescheduleBindedEvent () {
    //TODO: in test ControlPlaneSuite "ordering" test, bindedCompleteEvent can be true
    //TODO: that test case need to be polished, but not that urgent
    if (bindedCompleteEvent != null) {
      logTrace("reschedule " + toString + " completeEvent to " +
        (SimulationEngine.currentTime + remainingAppData / rate) +
        " current time:" + SimulationEngine.currentTime + " rate :" + rate +
        " appDataSize:" + remainingAppData)
      SimulationEngine.reschedule(bindedCompleteEvent,
        SimulationEngine.currentTime + remainingAppData / rate)
    }
  }

  def run () {
    logTrace("determine " + this + " rate to " + tempRate)
    changeRate(tempRate)
    status = RunningFlow
  }

  def close() {
    logTrace(this + " finishes at " + SimulationEngine.currentTime)
    lastChangePoint = SimulationEngine.currentTime
    status = CompletedFlow
    changeRate(0)
  }

  def setEgressLink (eLink : Link) {
    egressLink = eLink
    logTrace("set flow " + this + "'s egresslink as " + egressLink)
    var link = egressLink
    while (link != null) {
      hop += 1
      val lastlinkidx = trace_laststeptrack(trace.indexOf(link))._2
      link = {
        if (lastlinkidx != -1) trace(lastlinkidx)
        else null
      }
    }
  }

  def LastCheckPoint : Double = lastChangePoint

  override def toString() : String = ("Flow-" + srcIP + "-" + dstIP)

  def getEgressLink = egressLink

  def getIngressLink = trace(0)
}

object Flow {
  /**
   *
   * @param srcIP
   * @param dstIP
   * @param srcMac
   * @param dstMac
   * @param sPort
   * @param dPort
   * @param vlanID
   * @param prioritycode
   * @param appDataSize
   * @param fflag
   * @return
   */
  def apply(srcIP : String, dstIP : String, srcMac : String, dstMac : String,
            sPort : Short = 1, dPort : Short = 1, vlanID : Short = 0xffff.asInstanceOf[Short],
            prioritycode : Byte = 0, appDataSize : Double, fflag : Boolean = false) : Flow = {
    new Flow(srcIP, dstIP, srcMac, dstMac, vlanID, prioritycode, srcPort = sPort, dstPort = dPort,
      remainingAppData = appDataSize, floodflag = fflag)
  }
}