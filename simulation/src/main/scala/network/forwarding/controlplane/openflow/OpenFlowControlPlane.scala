package scalasem.network.forwarding.controlplane.openflow

import java.util.concurrent.Executors
import java.net.InetSocketAddress

import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.concurrent.Lock
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.Channel
import org.openflow.protocol._
import org.openflow.protocol.statistics._
import org.slf4j.LoggerFactory
import org.openflow.protocol.factory.BasicFactory
import org.openflow.protocol.action.{OFActionType, OFAction, OFActionOutput}

import packets._
import scalasem.network.topology._
import scalasem.network.forwarding.controlplane.DefaultControlPlane
import scalasem.util.XmlParser
import scalasem.network.traffic.Flow
import scalasem.network.forwarding.interface.OpenFlowPortManager
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.simengine.SimulationEngine

/**
 * this class implement the functions for routers to contact with
 * controller
 */
class OpenFlowControlPlane (private [openflow] val node : Node)
  extends DefaultControlPlane(node) with MessageListener {

  private val controllerIP = XmlParser.getString("scalasim.network.controlplane.openflow.controller.host", "127.0.0.1")
  private val controllerPort = XmlParser.getInt("scalasim.network.controlplane.openflow.controller.port", 6633)
  private var floodedflows = List[Flow]()


  private var lldpcnt = 0

  private [network] var toControllerChannel : Channel = null

  private [openflow] lazy val ofinterfacemanager = node.interfacesManager.
    asInstanceOf[OpenFlowPortManager]
  private [network] val ofmsgsender = new OpenFlowMsgSender

  private var config_flags : Short = 0
  private var miss_send_len : Short = 1000

  private [controlplane] val flowtables = new Array[OFFlowTable](
    XmlParser.getInt("scalasim.openflow.flowtablenum", 1))

  //the flows waiting for PACKET_OUT
  private val pendingFlowLock = new Lock
  private [controlplane] val pendingFlows = new mutable.HashMap[Int, Flow]
    with mutable.SynchronizedMap[Int, Flow]


  private val logger = LoggerFactory.getLogger("OpenFlowControlPlane")
  private val factory = new BasicFactory

  lazy val getSwitchDescription = {
    /*if (node.ip_addr.size < 1) "no description"
    else */
    //if (node.ip_addr.size < 1) println("fuck your dirty ASS " + node.nodetype.toString)
    node.ip_addr(0)
  }

  lazy val getMacAddress = {
    /*if (node.mac_addr.size < 1) "no hardware addr"
    else*/ node.mac_addr(0)
  }

  private def openflowInit() {
    for (i <- 0 until flowtables.length) flowtables(i) = new OFFlowTable(i.toShort, this)
  }

  private def generatePacketIn(bufferid : Int, inPortnum : Short, payload: Array[Byte],
                               reason: OFPacketIn.OFPacketInReason): OFPacketIn = {

    val packet_in_msg = factory.getMessage(OFType.PACKET_IN).asInstanceOf[OFPacketIn]
    packet_in_msg.setBufferId(bufferid)
      .setInPort(inPortnum)
      .setPacketData(payload)
      .setReason(OFPacketIn.OFPacketInReason.NO_MATCH)
      .setTotalLength(payload.size.toShort)
      .setLength((payload.size + 18).toShort)
      .setVersion(1)
    //logger.debug("frame size:" + payload.size + " total length of packet_in:" + packet_in_msg.getTotalLength)
    packet_in_msg
  }

  def FlowTables = flowtables

  def sendPacketInToController(flow : Flow, inlink: Link, ethernetFramedata: Array[Byte]) {

    def getFreeBufferID : Int = {
      val bufferIDs = pendingFlows.keys
      if (bufferIDs.size == 0) 1
      else {
        bufferIDs.foldLeft(-1)((b, a) => Math.max(b, a))
      }
    }
    assert(ofinterfacemanager.linkphysicalportsMap.contains(inlink))
    val inport = ofinterfacemanager.linkphysicalportsMap(inlink)
    pendingFlowLock.acquire()
    val bufferid = getFreeBufferID + 1
    val packetin_msg = generatePacketIn(bufferid,
      inport.getPortNumber, ethernetFramedata,
      OFPacketIn.OFPacketInReason.NO_MATCH)
    logger.debug("send PACKET_IN " + packetin_msg.toString +
      "to controller for table missing at node " + node)
    logger.debug("buffering flow " + flow + " at buffer " + bufferid +
      " at node " + node)
    pendingFlows += (bufferid -> flow)
    pendingFlowLock.release()
    ofmsgsender.sendMessageToController(toControllerChannel, packetin_msg)
  }

  def sendLLDPtoController (l : Link, lldpData : Array[Byte]) {
    assert(ofinterfacemanager.linkphysicalportsMap.contains(l))
    val port = ofinterfacemanager.linkphysicalportsMap(l)
    //send out packet_in
    lldpcnt += 1
    ofmsgsender.sendMessageToController(toControllerChannel,
      generatePacketIn(-1, port.getPortNumber, lldpData, OFPacketIn.OFPacketInReason.ACTION))
  }

  private def replyLLDP (pktoutMsg : OFPacketOut) {
    //send out through all ports
    val outport = pktoutMsg.getActions.get(0).asInstanceOf[OFActionOutput].getPort
    val outlink = ofinterfacemanager.reverseSelection(outport)
    val neighbor = Link.otherEnd(outlink, node)
    val lldpdata = pktoutMsg.getPacketData
    //TODO: only support the situation that all routers are openflow-enabled
    if (neighbor.nodetype != HostType)
      neighbor.controlplane.asInstanceOf[OpenFlowControlPlane].sendLLDPtoController(outlink, lldpdata)
  }

  //build channel to the controller
  def connectToController() {
    val clientfactory = new NioClientSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool())
    val clientbootstrap = new ClientBootstrap(clientfactory)
    clientbootstrap.setOption("connectTimeoutMillis", 600000)
    clientbootstrap.setOption("keepAlive", true)
    clientbootstrap.setPipelineFactory(new OpenFlowMsgPipelineFactory(this))
    while (true) {
      try {
        clientbootstrap.connect(new InetSocketAddress(controllerIP, controllerPort))
        logger.info(node.ip_addr(0) + " connected successfully")
        return
      }
      catch {
        case e : Exception => {
          logger.warn(node.ip_addr(0) + " connected failed")
        }
      }
    }
  }

  def disconnectFormController() {
    if (toControllerChannel != null && toControllerChannel.isConnected)
      toControllerChannel.disconnect()
  }

  private def setParameter(f : Short, m : Short) {
    config_flags = f
    miss_send_len = m
  }



  private def getSwitchFeature (xid : Short) : OFFeaturesReply = {
    //TODO: specify the switch features
    //(dpid, buffer, n_tables, capabilities, physical port
    val featurereply = factory.getMessage(OFType.FEATURES_REPLY).asInstanceOf[OFFeaturesReply]
    val featurelist = (node.DPID, 1000, flowtables.length, 0xff, ofinterfacemanager.linkphysicalportsMap.values.toList)
    featurereply.setDatapathId(featurelist._1)
    featurereply.setBuffers(featurelist._2)
    featurereply.setTables(featurelist._3.toByte)
    featurereply.setCapabilities(featurelist._4)
    featurereply.setPorts(featurelist._5.asJava)
    featurereply.setLength((32 + featurereply.getPorts.size * OFPhysicalPort.MINIMUM_LENGTH).toShort)
    //TODO: only support output action for now
    featurereply.setActions(1) //only support output action for now
    featurereply.setXid(xid)
    featurereply.setVersion(1)
    featurereply.setType(OFType.FEATURES_REPLY)
    featurereply
  }

  private def generateBarrierReply(barrierreq : OFBarrierRequest): OFBarrierReply = {
    val barrierReply = factory.getMessage(OFType.BARRIER_REPLY).asInstanceOf[OFBarrierReply]
    barrierReply.setType(OFType.BARRIER_REPLY)
    barrierReply.setVersion(1)
    barrierReply.setXid(barrierreq.getXid)
    barrierReply
  }

  private def generateSwitchConfigReply(xid : Short, version : Byte): OFGetConfigReply = {
    val getconfigreply = factory.getMessage(OFType.GET_CONFIG_REPLY).asInstanceOf[OFGetConfigReply]
    getconfigreply.setFlags(config_flags)
    getconfigreply.setMissSendLength(miss_send_len)
    getconfigreply.setXid(xid)
    getconfigreply.setVersion(1)
    getconfigreply.setType(OFType.GET_CONFIG_REPLY)
    getconfigreply
  }

  private def generateEchoReply(echoreq: OFEchoRequest): OFEchoReply = {
    val echoreply = factory.getMessage(OFType.ECHO_REPLY).asInstanceOf[OFEchoReply]
    val payloadlength = {
      if (echoreq.getPayload != null) echoreq.getPayload.length
      else 0
    }
    echoreply.setXid(echoreq.getXid)
    echoreply.setPayload(echoreq.getPayload)
    echoreply.setLength((OFEchoReply.MINIMUM_LENGTH + payloadlength).toShort)
    echoreply.setVersion(1)
    echoreply.setType(OFType.ECHO_REPLY)
    echoreply
  }


  private def processFlowMod(offlowmod: OFFlowMod) {
    offlowmod.getCommand match {
      case OFFlowMod.OFPFC_DELETE => {
        if (offlowmod.getMatch.getWildcards == OFMatch.OFPFW_ALL) {
          //clear to initialize matchfield tables;
          flowtables.foreach(table => table.clear())
        }
      }
      case OFFlowMod.OFPFC_ADD => {
        logger.trace("receive OFPFC_ADD:" + offlowmod.toString + " at " + node.ip_addr(0))
        //table(0) for openflow 1.0

        if (offlowmod.getActions.size() > 0) {
          var outport = -1
          for (action <- offlowmod.getActions) {
            if (action.isInstanceOf[OFActionOutput]) {
              outport = action.asInstanceOf[OFActionOutput].getPort
            }
          }
          RIBOut.synchronized{
            if (outport >= 0)
              insertOutPath(offlowmod.getMatch,
                ofinterfacemanager.reverseSelection(outport.asInstanceOf[Short]))
            flowtables(0).addFlowTableEntry(offlowmod)
            logDebug("flowtable length:" + flowtables(0).entries.size + " at " + node.ip_addr(0))
          }
        } else {
          logger.debug("clear buffer " + offlowmod.getBufferId)
          pendingFlows -= offlowmod.getBufferId
        }

      }
      case _ => throw new Exception("unrecognized OFFlowMod command type:" + offlowmod.getCommand)
    }
  }

  /**
   * called by the openflowhandler
   * routing according to the pktoutmsg
   * @param pktoutmsg
   */
  def processOFPacketOut (pktoutmsg : OFPacketOut) {

    /*def isLLDP: Boolean = {
      val ethernet = pktoutmsg.getPacketData.asInstanceOf[Ethernet]
      ethernet.getPayload.isInstanceOf[LLDP]
    }*/

    if (pktoutmsg.getBufferId == -1) {
      //the packet data is included in the packoutmsg
      //assume bufferid - 1 is LLDP packet
      replyLLDP(pktoutmsg)
    } else {
      //TODO: is there any difference if we are on packet-level simulation?
      if (!pendingFlows.contains(pktoutmsg.getBufferId)) {
        //in case of duplicate packet out message
        logger.debug("receive non-existing bufid = " + pktoutmsg.getBufferId +
          " on " + node.ip_addr(0))
        return
      }
      val pendingflow = pendingFlows(pktoutmsg.getBufferId)
      for (action : OFAction <- pktoutmsg.getActions.asScala) {
        action.getType match {
          //only support output for now
          case OFActionType.OUTPUT => {
            val outaction = action.asInstanceOf[OFActionOutput]
            val matchfield = OFFlowTable.createMatchField(flow = pendingflow)
            val ilink = ofinterfacemanager.reverseSelection(pktoutmsg.getInPort)
            val olink = ofinterfacemanager.reverseSelection(outaction.getPort)
            log.trace("removing flow " + pendingflow + " from pending buffer " +
              pktoutmsg.getBufferId + " at node " + node)
            pendingFlowLock.acquire()
            pendingFlows -= (pktoutmsg.getBufferId)
            pendingFlowLock.release()
            if (outaction.getPort == OFPort.OFPP_FLOOD.getValue) {
              //flood the flow since the controller does not know the location of the destination
              if (!floodedflows.contains(pendingflow)) {
                logTrace("flood the flow " + pendingflow + " at " + node)
                pendingflow.floodflag = false
                floodoutFlow(node, pendingflow, matchfield, ilink)
                floodedflows = floodedflows :+ pendingflow
              } else {
                logger.warn("duplicate flooded flow " + pendingflow + " at " + node.ip_addr(0))
              }
            } else {
              logTrace("forward the flow " + pendingflow + " through " + olink + " at node " + node)
              logger.debug("receive PACKET_OUT:" + pktoutmsg + ", the output link is " +
                outaction.getPort + ", " + olink.toString + ", at " + node.ip_addr(0) +
                " for " + pendingflow.toString())
              if (ilink != olink) {
                pendingflow.floodflag = false
                forward(node, olink, ilink, pendingflow, matchfield)
              } else {
                deleteEntry(matchfield)
              }
            }
          }
          case _ => throw new Exception("unrecognizable action")
        }
      }
    }
  }

  /**
   * send the flow counters to the controller actively
   */
  def sendFlowCounters() {
    val ofstatreply = factory.getMessage(OFType.STATS_REPLY).asInstanceOf[OFStatisticsReply]
    val statreplylist = flowtables(0).getAllFlowStat
    //resemble the ofstatreply
    ofstatreply.setStatistics(statreplylist.toList.asJava)
    ofstatreply.setStatisticType(OFStatisticsType.FLOW)
    ofstatreply.setStatisticsFactory(factory)
    //calculate the message length
    var l = 0
    statreplylist.foreach(statreply => l += statreply.getLength)
    ofstatreply.setLength((l + ofstatreply.getLength).toShort)
    ofstatreply.setXid(0)
    ofstatreply.setVersion(1)
    ofstatreply.setType(OFType.STATS_REPLY)
    ofmsgsender.pushInToBuffer(ofstatreply)
  }

  private def generateDataplaneDesc(ofstatreq : OFStatisticsRequest) = {
    val statdescreply = factory.getStatistics(OFType.STATS_REPLY,OFStatisticsType.DESC)
      .asInstanceOf[OFDescriptionStatistics]
    val statreply = factory.getMessage(OFType.STATS_REPLY).asInstanceOf[OFStatisticsReply]
    //TODO: descriptions are not complete
    val statlist = new ListBuffer[OFStatistics]
    statdescreply.setDatapathDescription(getSwitchDescription)
    statdescreply.setHardwareDescription(getMacAddress)
    statdescreply.setManufacturerDescription("simulated router")
    statdescreply.setSoftwareDescription("simulated router software")
    statdescreply.setSerialNumber("1")
    statlist += statdescreply
    statreply.setStatisticType(OFStatisticsType.DESC)
    statreply.setStatistics(statlist.toList.asJava)
    statreply.setStatisticsFactory(factory)
    statreply.setLength((statdescreply.getLength+ statreply.getLength).toShort)
    statreply.setXid(ofstatreq.getXid)
    statreply.setVersion(1)
    statreply.setType(OFType.STATS_REPLY)
    statreply
  }

  private def processFlowStatisticsQuery (ofstatrequest: OFStatisticsRequest) = {

    def queryAllTablesByFlowStatRequest(flowstatreq : OFFlowStatisticsRequest) :
      List[OFStatistics] = {
      val statreplylist = new ListBuffer[OFStatistics]
      //query all tables
      for (flowtable <- flowtables) {
        flowtable.queryByFlowStatRequest(flowstatreq).foreach(
          flowstatreply => statreplylist += flowstatreply)
      }
      statreplylist.toList
    }

    val ofstatreply = factory.getMessage(OFType.STATS_REPLY).asInstanceOf[OFStatisticsReply]
    val offlowstatreq = ofstatrequest.getStatistics.asScala
    var statreplylist : List[OFStatistics] = null
    for (statreq <- offlowstatreq) {
      val flowstatreq = statreq.asInstanceOf[OFFlowStatisticsRequest]
      if (flowstatreq.getTableId == -1) {
        statreplylist = queryAllTablesByFlowStatRequest(flowstatreq)
      } else {
        //query a single table
        statreplylist = flowtables(flowstatreq.getTableId).queryByFlowStatRequest(flowstatreq)
      }
    }
    //resemble the ofstatreply
    ofstatreply.setStatistics(statreplylist.toList.asJava)
    ofstatreply.setStatisticType(OFStatisticsType.FLOW)
    ofstatreply.setStatisticsFactory(factory)
    //calculate the message length
    var l = 0
    statreplylist.foreach(statreply => l += statreply.getLength)
    ofstatreply.setLength((l + ofstatreply.getLength).toShort)
    ofstatreply.setXid(ofstatrequest.getXid)
    ofstatreply.setVersion(1)
    ofstatreply.setType(OFType.STATS_REPLY)
    ofmsgsender.pushInToBuffer(ofstatreply)
  }

  private def processAggregateStatisticsQuery (ofstatrequest: OFStatisticsRequest) = {
    val ofstatreply = factory.getMessage(OFType.STATS_REPLY).asInstanceOf[OFStatisticsReply]
    val offlowstatreq = ofstatrequest.getStatistics.asScala
    val statreplylist = new ListBuffer[OFStatistics]
    val stataggreply = factory.getStatistics(OFType.STATS_REPLY, OFStatisticsType.AGGREGATE)
      .asInstanceOf[OFAggregateStatisticsReply]
    for (statreq <- offlowstatreq) {
      val flowstatreq = statreq.asInstanceOf[OFAggregateStatisticsRequest]
      if (flowstatreq.getTableId == -1) {
        for (i <- 0 until flowtables.length) {
          val referred_table = flowtables(i)
          stataggreply.setFlowCount(stataggreply.getFlowCount + referred_table.tableCounter.referencecount)
          stataggreply.setPacketCount(stataggreply.getPacketCount + referred_table.tableCounter.packetlookup
            + referred_table.tableCounter.packetmatches)
          stataggreply.setByteCount(stataggreply.getByteCount + referred_table.tableCounter.flowbytes)
        }
      } else {
        val referred_table = flowtables(flowstatreq.getTableId)
        stataggreply.setFlowCount(referred_table.tableCounter.referencecount)
        stataggreply.setPacketCount(referred_table.tableCounter.packetlookup +
          referred_table.tableCounter.packetmatches)
        stataggreply.setByteCount(referred_table.tableCounter.flowbytes)
      }
    }
    //resemble the ofstatreply
    statreplylist += stataggreply
    ofstatreply.setStatistics(statreplylist.toList.asJava)
    ofstatreply.setStatisticType(OFStatisticsType.AGGREGATE)
    ofstatreply.setStatisticsFactory(factory)
    //calculate the message length
    ofstatreply.setLength((stataggreply.getLength + ofstatreply.getLength).toShort)
    ofstatreply.setXid(ofstatrequest.getXid)
    ofstatreply.setType(OFType.STATS_REPLY)
    ofstatreply.setVersion(1)
    ofmsgsender.pushInToBuffer(ofstatreply)
  }

  private def generateHelloMsg(hellomsg : OFHello) = {
    val helloreply = factory.getMessage(OFType.HELLO).asInstanceOf[OFHello]
    helloreply.setLength(8)
    helloreply.setType(OFType.HELLO)
    helloreply.setXid(hellomsg.getXid)
    helloreply.setVersion(1)
    helloreply
  }

  override def handleMessage(msg: OFMessage) {
    msg.getType match {
      case OFType.SET_CONFIG => {
        logger.trace(node + " received a set_config message")
        val setconfigmsg = msg.asInstanceOf[OFSetConfig]
        setParameter(setconfigmsg.getFlags, setconfigmsg.getMissSendLength)
      }
      case OFType.HELLO => {
        val hellomsg = msg.asInstanceOf[OFHello]
        logger.trace(node + " received a hello message, version number:" + hellomsg.getVersion)
        ofmsgsender.pushInToBuffer(generateHelloMsg(msg.asInstanceOf[OFHello]))
      }
      case OFType.FEATURES_REQUEST => {
        val featurereply = getSwitchFeature(msg.getXid.toShort)
        ofmsgsender.pushInToBuffer(featurereply)
      }
      case OFType.GET_CONFIG_REQUEST => {
        ofmsgsender.pushInToBuffer(
          generateSwitchConfigReply(msg.getXid.toShort, msg.getVersion))
      }
      case OFType.FLOW_MOD => processFlowMod(msg.asInstanceOf[OFFlowMod])
      case OFType.PACKET_OUT => processOFPacketOut(msg.asInstanceOf[OFPacketOut])
      case OFType.ECHO_REQUEST => ofmsgsender.pushInToBuffer(
        generateEchoReply(msg.asInstanceOf[OFEchoRequest]))
      case OFType.STATS_REQUEST => {
        val ofstatrequest = msg.asInstanceOf[OFStatisticsRequest]
        ofstatrequest.getStatisticType match {
          case OFStatisticsType.DESC => {
            logger.trace(node +  " received a desc stat request")
            ofmsgsender.pushInToBuffer(generateDataplaneDesc(ofstatrequest))
          }
          case OFStatisticsType.FLOW => processFlowStatisticsQuery(ofstatrequest)
          case OFStatisticsType.AGGREGATE => processAggregateStatisticsQuery(ofstatrequest)
          case _ => {}
        }
      }
      case OFType.BARRIER_REQUEST => {
        logger.trace(node +  " received a barrier request")
        ofmsgsender.pushInToBuffer(generateBarrierReply(msg.asInstanceOf[OFBarrierRequest]))
      }
      case _ => {}
    }
  }

  override def deleteEntry(ofmatch : OFMatch) {
    val matchfield = OFFlowTable.createMatchFieldFromOFMatch(ofmatch)
    logTrace("delete entry:" + matchfield + " at node:" + this)
    //RIBIn -= matchfield
    RIBOut -= matchfield
  }

  //abstract methods
  override def selectNextHop(flow: Flow, matchfield: OFMatchField, inPort: Link): Link = {
    if (flow.hasBindedCompleteEvent) {
      //TODO: should allow the duplicate flow
      return null
    }
    if (!RIBOut.contains(matchfield)) {
      //send packet_in to controller

      val debugstr = "miss the matchfield " + matchfield.toString + " with hashcode " + matchfield.hashCode
      logDebug(debugstr)
      val dummypayload = new Array[Byte](1)
      dummypayload(0) = (0).toByte
      val ethernetFrame = new Ethernet
      ethernetFrame.setEtherType(Ethernet.TYPE_IPv4)
        .setSourceMACAddress(matchfield.getDataLayerSource)
        .setDestinationMACAddress(matchfield.getDataLayerDestination)
        .setPayload(new IPv4()
        .setSourceAddress(matchfield.getNetworkSource)
        .setDestinationAddress(matchfield.getNetworkDestination)
        .setVersion(4)
        .setPayload(new TCP()
        .setSourcePort(matchfield.getTransportSource)
        .setDestinationPort(matchfield.getTransportDestination)
        .setPayload(new Data(dummypayload))))
      val serializedData = ethernetFrame.serialize
      sendPacketInToController(flow , inPort, serializedData)
      null
    } else {
      //openflow 1.0
      logDebug("hit RIBOut with " + matchfield.toString + " at " + node.ip_addr(0))
      //assume return only one result
      logDebug(flowtables(0).matchFlow(matchfield).toString())
      for (entryattach <- flowtables(0).matchFlow(matchfield)) {
        //TODO: support other actions
        entryattach.actions.foreach(action =>
          if (action.isInstanceOf[OFActionOutput]) return RIBOut(matchfield))
      }
      null
    }
  }

  openflowInit()
}
