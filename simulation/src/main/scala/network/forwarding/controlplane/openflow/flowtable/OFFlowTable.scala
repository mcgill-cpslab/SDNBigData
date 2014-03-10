package scalasem.network.forwarding.controlplane.openflow.flowtable

import scala.collection.mutable.{ListBuffer, HashMap, SynchronizedMap}
import scala.collection.JavaConversions._

import org.openflow.util.U32
import org.openflow.protocol.{OFType, OFFlowMod, OFMatch}
import org.openflow.protocol.action.OFActionOutput
import org.openflow.protocol.statistics.{OFStatistics, OFFlowStatisticsReply, OFFlowStatisticsRequest, OFStatisticsType}
import org.openflow.protocol.factory.BasicFactory

import scalasem.network.forwarding.controlplane.openflow._
import scalasem.simengine.SimulationEngine
import scalasem.network.traffic.Flow
import scalasem.util.{XmlParser, Logging, ReflectionUtil}

import utils.IPAddressConvertor

class OFFlowTable (private [openflow] val tableid : Short, ofcontrolplane : OpenFlowControlPlane)
  extends Logging {

  private [openflow] val entries : HashMap[OFMatchField, OFFlowTableEntryBase] =
    new HashMap[OFMatchField, OFFlowTableEntryBase] with
      SynchronizedMap[OFMatchField, OFFlowTableEntryBase]
  private [openflow] val tableCounter : OFTableCount = new OFTableCount
  private val messageFactory = new BasicFactory
  private val entryClzName = XmlParser.getString("scalasim.openflow.table.entryclass",
    "scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTableEntryBase")

  def TableCounter = tableCounter

  def clear() {
    logDebug("entries were cleared in " + ofcontrolplane.node.ip_addr(0))
    entries.clear()
  }

  def matchFlow(flowmatch : OFMatch, topk : Int = 1) : List[OFFlowTableEntryBase] = {
    assert(topk > 0)
    var ret = List[OFFlowTableEntryBase]()
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(flowmatch)
    logDebug("matching flow, entry length:" + entries.size)
    entries.foreach(entry => {
    //  logDebug(entry._1.toCompleteString() + "\t" + matchfield.toCompleteString())
      if (entry._1.matching(matchfield)) ret = ret :+ entry._2
    })
    ret.sortWith(_.priority > _.priority).slice(0, topk)
  }

  private def queryTable(matchrule : OFMatchField, topk : Int = 1) : List[OFFlowTableEntryBase] = {
    assert(topk > 0)
    var ret = List[OFFlowTableEntryBase]()
    entries.foreach(entry => {if (matchrule.matching(entry._1)) ret = ret :+ entry._2})
    ret.sortWith(_.priority > _.priority).slice(0, topk)
  }

  def queryTableByMatch(ofmatch : OFMatch) : List[OFFlowTableEntryBase] = {
    if (ofmatch.getWildcards == -1) {
      logDebug("return all flows: " + entries.values.toList.length)
      entries.values.toList
    } else {
      queryTable(OFFlowTable.createMatchFieldFromOFMatch(ofmatch, ofmatch.getWildcards))
    }
  }

  def queryTableByMatchAndOutport (match_field : OFMatch, outport_num : Short,
                                 topk : Int = 1) : List[OFFlowTableEntryBase] = {

    def containsOutputAction (p : OFFlowTableEntryBase) : OFActionOutput = {
      for (action <- p.actions) {
        if (action.isInstanceOf[OFActionOutput]) return action.asInstanceOf[OFActionOutput]
      }
      null
    }

    def filterByOutputPort (outaction : OFActionOutput, port_num : Short) : Boolean = {
      if (outaction == null) return false
      if (port_num == -1) return true
      port_num == outaction.getPort
    }

    val filteredByMatch = queryTableByMatch(match_field)
    logTrace("filteredByMatchLength: " + filteredByMatch.length)
    if (outport_num == -1) return filteredByMatch
    filteredByMatch.filter(p => filterByOutputPort(containsOutputAction(p), outport_num)).toList.
      sortWith(_.priority > _.priority).slice(0, topk)
  }

  def removeEntry (matchfield : OFMatchField) {
    logTrace(matchfield + " was removed from " + ofcontrolplane.node.ip_addr(0))
    entries -= matchfield
  }

  private def getNewEntry: OFFlowTableEntryBase = {
    val clz = Class.forName(entryClzName)
    val clzCtor = clz.getConstructor(this.getClass)
    clzCtor.newInstance(this).asInstanceOf[OFFlowTableEntryBase]
  }

  /**
   *
   * @param flow_mod
   * @return the updated entries
   */
  def addFlowTableEntry (flow_mod : OFFlowMod) = {
    assert(flow_mod.getCommand == OFFlowMod.OFPFC_ADD)
    logDebug(ofcontrolplane.node.ip_addr(0) + " insert flow table entry with " + flow_mod.getMatch)
    val newEntryValue = getNewEntry
    newEntryValue.ofmatch = flow_mod.getMatch
    newEntryValue.priority = flow_mod.getPriority
    flow_mod.getActions.toList.foreach(f => newEntryValue.actions += f)
    //schedule matchfield entry clean event
    newEntryValue.flowHardExpireMoment =
      (SimulationEngine.currentTime + flow_mod.getHardTimeout).toInt
    newEntryValue.flowIdleDuration = flow_mod.getIdleTimeout
    newEntryValue.refreshlastAccessPoint()
    entries += (OFFlowTable.createMatchFieldFromOFMatch(newEntryValue.ofmatch,
      newEntryValue.ofmatch.getWildcards) -> newEntryValue)
    entries
  }

  private def generateFlowStatisticalReplyFromFlowEntryList (flowlist: List[OFFlowTableEntryBase]) = {
    val replylist = new ListBuffer[OFStatistics]
    flowlist.foreach(flowentry => {
      val offlowstatreply = messageFactory.getStatistics(OFType.STATS_REPLY, OFStatisticsType.FLOW)
        .asInstanceOf[OFFlowStatisticsReply]
      var actionlistlength = 0
      flowentry.actions.foreach(action => actionlistlength += action.getLength)
      offlowstatreply.setMatch(flowentry.ofmatch)
      offlowstatreply.setTableId(flowentry.table.tableid.toByte)
      offlowstatreply.setDurationNanoseconds(flowentry.counter.durationNanoSeconds)
      offlowstatreply.setDurationSeconds(flowentry.counter.durationSeconds)
      offlowstatreply.setPriority(0)
      offlowstatreply.setIdleTimeout((flowentry.flowIdleDuration -
        (SimulationEngine.currentTime - flowentry.getLastAccessPoint)).toShort)
      offlowstatreply.setHardTimeout((flowentry.flowHardExpireMoment -
        SimulationEngine.currentTime).toShort)
      offlowstatreply.setCookie(0)
      offlowstatreply.setPacketCount(flowentry.counter.receivedpacket)
      offlowstatreply.setByteCount(flowentry.counter.receivedbytes)
      offlowstatreply.setActions(flowentry.actions)
      offlowstatreply.setLength((88 + actionlistlength).toShort)
      replylist += offlowstatreply
    })
    replylist.toList
  }

  def getAllFlowStat : List[OFStatistics] =
    generateFlowStatisticalReplyFromFlowEntryList(queryTableByMatch(new OFMatch))


  def queryByFlowStatRequest(offlowstatreq: OFFlowStatisticsRequest): List[OFStatistics] = {
    val qualifiedflows = queryTableByMatchAndOutport(offlowstatreq.getMatch,
      offlowstatreq.getOutPort)
    logTrace("qualified matchfield number: " + qualifiedflows.length)
    generateFlowStatisticalReplyFromFlowEntryList(qualifiedflows)
  }
}

object OFFlowTable {

  /**
   *
   * @param flow
   * @param wcard
   * @return
   */
  def createMatchField(flow: Flow,
                       wcard: Int = OFMatch.OFPFW_ALL & OFMatch.OFPFW_IN_PORT) : OFMatchField = {
    val matchfield = new OFMatchField()
    matchfield.setWildcards(wcard)
    matchfield.setInputPort(flow.inport)
    matchfield.setDataLayerDestination(flow.dstMac)
    matchfield.setDataLayerSource(flow.srcMac)
    matchfield.setDataLayerType(0x800)
    matchfield.setDataLayerVirtualLan(flow.vlanID)
    matchfield.setNetworkProtocol(6)
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt(flow.dstIP)))
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt(flow.srcIP)))
    matchfield
  }

  /**
   * generate the OFMatchField from a OFMatch
   * @param ofmatch
   * @param wcard
   * @return
   */
  def createMatchFieldFromOFMatch(ofmatch : OFMatch,
                                  wcard : Int = OFMatch.OFPFW_ALL & OFMatch.OFPFW_IN_PORT) : OFMatchField = {
    val matchfield = new OFMatchField
    matchfield.setWildcards(wcard)
    matchfield.setInputPort(ofmatch.getInputPort)
    matchfield.setDataLayerDestination(ofmatch.getDataLayerDestination)
    matchfield.setDataLayerSource(ofmatch.getDataLayerSource)
    matchfield.setDataLayerType(0x800)
    matchfield.setDataLayerVirtualLan(ofmatch.getDataLayerVirtualLan)
    matchfield.setNetworkProtocol(6)
    matchfield.setNetworkDestination(ofmatch.getNetworkDestination)
    matchfield.setNetworkSource(ofmatch.getNetworkSource)
    matchfield
  }
}