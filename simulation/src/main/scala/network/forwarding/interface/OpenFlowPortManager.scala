package scalasem.network.forwarding.interface

import java.util

import scala.collection.JavaConversions._
import scala.Some
import scala.collection.mutable.HashMap

import org.openflow.protocol._
import org.openflow.protocol.statistics.{OFStatistics, OFPortStatisticsReply, OFPortStatisticsRequest, OFStatisticsType}
import org.openflow.protocol.factory.BasicFactory
import org.openflow.util.HexString

import scalasem.network.topology.{HostType, GlobalDeviceManager, Link, Node}
import scalasem.network.forwarding.controlplane.openflow.{OpenFlowControlPlane, OFPortCount, MessageListener}
import scalasem.util.Logging


class OpenFlowPortManager(node: Node) extends DefaultInterfacesManager(node)
  with MessageListener with Logging {

  private [forwarding] val linkphysicalportsMap = new HashMap[Link, OFPhysicalPort]
  private [forwarding] val physicalportsMap = new HashMap[Short, OFPhysicalPort]//port number -> port

  private [forwarding] val portcounters = new HashMap[Short, OFPortCount]//portnum -> counter

  private val factory = new BasicFactory

  def getPhysicalPort(portNum : Short) = physicalportsMap.getOrElse(portNum, null)

  def getPortByLink (l : Link) = {
    assert(linkphysicalportsMap.contains(l))
    linkphysicalportsMap(l)
  }

  /**
   * choose the link via port number
   * @param portNum the port number
   * @return the link
   */
  def reverseSelection (portNum : Short) : Link = {
    linkphysicalportsMap.find(link_port_pair => link_port_pair._2.getPortNumber == portNum) match {
      case Some(lppair) => lppair._1
      case None => null
    }
  }

  def getPortCounter(portnum : Short) = portcounters(portnum)

  private def addOFPhysicalPort(l : Link, portID : Short) {
    val port = new OFPhysicalPort
    GlobalDeviceManager.globalDeviceCounter += 1
    //port number
    port.setPortNumber(portID)
    //port hardware address
    val hwaddrhexstr = HexString.toHexString(GlobalDeviceManager.globalDeviceCounter, 6)
    port.setHardwareAddress(HexString.fromHexString(hwaddrhexstr))
    //port name
    port.setName(hwaddrhexstr.replaceAll(":", ""))
    //port features
    //TODO: limited support feature?
    var feature = 0
    if (l.bandwidth == 100) {
      feature = 1 << 3
    }
    else {
      if (l.bandwidth == 1000) feature = 1 << 5
    }
    port.setAdvertisedFeatures(feature)
    port.setCurrentFeatures(feature)
    port.setPeerFeatures(feature)
    port.setSupportedFeatures(feature)
    port.setConfig(0)
    port.setState(0)
    linkphysicalportsMap += l -> port
    physicalportsMap += (port.getPortNumber -> port)
    portcounters += (port.getPortNumber -> new OFPortCount(port.getPortNumber))
    logDebug("add physical port " + port.getPortNumber + " as " + l.toString +
      " at node " + node.ip_addr(0))
  }

  override def registerOutgoingLink(l : Link) {
    super.registerOutgoingLink(l)
    addOFPhysicalPort(l, (outlinks.size + inlinks.size).toShort)
  }

  override def registerIncomeLink(l : Link) {
    super.registerIncomeLink(l)
    if (node.nodetype != HostType) {
      addOFPhysicalPort(l, (outlinks.size + inlinks.size).toShort)
    }
  }

  private def queryPortCounters(portnum : Short) : util.List[OFStatistics] = {
    def generatestatportreply (portnum : Short, counter : OFPortCount) = {
      val statportreply = factory.getStatistics(OFType.STATS_REPLY, OFStatisticsType.PORT)
        .asInstanceOf[OFPortStatisticsReply]
      statportreply.setCollisions(counter.collisions)
      statportreply.setPortNumber(portnum)
      statportreply.setReceiveBytes(counter.receivedbytes)
      statportreply.setreceivePackets(counter.receivedpacket)
      statportreply.setReceiveDropped(counter.receivedrops)
      statportreply.setreceiveErrors(counter.receivederror)
      statportreply.setReceiveFrameErrors(counter.receiveframe_align_error)
      statportreply.setReceiveOverrunErrors(counter.receive_overrun_error)
      statportreply.setReceiveCRCErrors(counter.receive_crc_error)
      statportreply.setTransmitPackets(counter.transmittedpacket)
      statportreply.setTransmitBytes(counter.transmittedbytes)
      statportreply.setTransmitDropped(counter.transmitdrops)
      statportreply.setTransmitErrors(counter.transmiterror)
      statportreply
    }
    val statList = new util.ArrayList[OFStatistics]
    if (portnum == -1) {
      val counters = portcounters
      for (counter_pair <- counters) {
        statList += generatestatportreply(counter_pair._1, counter_pair._2)
      }
    } else {
      statList += generatestatportreply(portnum, portcounters(portnum))
    }
    statList
  }

  def handleMessage(msg: OFMessage) {
    msg.getType match {
      case OFType.STATS_REQUEST => {
        val ofstatrequest = msg.asInstanceOf[OFStatisticsRequest]
        val ofstatreply = factory.getMessage(OFType.STATS_REPLY).asInstanceOf[OFStatisticsReply]
        ofstatrequest.getStatisticType match {
          case OFStatisticsType.PORT => {
            logTrace("received a port statistical request")
            val statportreqmsg = ofstatrequest.asInstanceOf[OFStatisticsRequest]
            val statportreqs = statportreqmsg.getStatistics
            val statList = new util.ArrayList[OFStatistics]
            statportreqs.foreach(statreq => {
              queryPortCounters(statreq.asInstanceOf[OFPortStatisticsRequest].getPortNumber)
                .foreach(statportreply => statList += statportreply)
            })
            //resemble ofstatreply
            ofstatreply.setStatistics(statList)
            ofstatreply.setStatisticsFactory(factory)
            ofstatreply.setStatisticType(OFStatisticsType.PORT)
            ofstatreply.setXid(ofstatrequest.getXid)
            ofstatreply.setVersion(1)
            ofstatreply.setType(OFType.STATS_REPLY)
            //calculate the length
            ofstatreply.setLength((12 + statList.length * statList(0).getLength).toShort)
            node.controlplane.asInstanceOf[OpenFlowControlPlane].ofmsgsender.pushInToBuffer(ofstatreply)
          }
          case _ => {}
        }
      }
      case _ => {}
    }
  }

  serveNode = node
}
