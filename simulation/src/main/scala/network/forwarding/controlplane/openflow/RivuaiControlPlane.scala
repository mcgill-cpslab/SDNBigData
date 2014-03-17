package scalasem.network.forwarding.controlplane.openflow

import scala.collection.JavaConversions._

import org.openflow.protocol.{OFMatch, OFFlowMod, OFType, OFMessage}
import org.openflow.protocol.action.OFActionOutput

import scalasem.network.events.RivuaiRateControlEvent
import scalasem.network.forwarding.controlplane.openflow.flowtable.{OFRivuaiFlowTableEntry, OFFlowTableBase}
import scalasem.network.forwarding.dataplane.RivuaiDataPlane
import scalasem.network.topology.{ToRRouterType, Router}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser

class RivuaiControlPlane(router: Router) extends OpenFlowControlPlane(router) {

  def getAllowedRate(flow: Flow): Double =  {
    if (router.nodeType == ToRRouterType) {
      val matchfield = OFFlowTableBase.createMatchField(flow)
      // should always return top 1 entry
      val entry = flowtables(0).queryTableByMatch(matchfield)
      if (entry.length > 0) {
        entry.asInstanceOf[OFRivuaiFlowTableEntry].ratelimit
      }
      else {
        XmlParser.getDouble("scalasim.rivuai.baserate", 100.0)
      }
    } else {
      // not ingress/egress switch
      Double.MaxValue
    }
  }

  private def startRateController() {
    // only start in edge
    if (router.nodeType == ToRRouterType) {
      val startTime = SimulationEngine.startTime
      val endTime = SimulationEngine.endTime
      val frequency = XmlParser.getDouble("simengine.rivuai.frequency", 1)

      for (s <- startTime until endTime by frequency) {
        val rcEvent = new RivuaiRateControlEvent(node, s)
        SimulationEngine.addEvent(rcEvent)
      }
    }
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
        logTrace("receive OFPFC_ADD:" + offlowmod.toString + " at " + node.ip_addr(0))
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
                ofinterfacemanager.getLinkByPortNum(outport.asInstanceOf[Short]))
            flowtables(0).addFlowTableEntry(offlowmod)
            logDebug("flowtable length:" + flowtables(0).entries.size + " at " + node.ip_addr(0))
          }
        } else {
          logDebug("clear buffer " + offlowmod.getBufferId)
          pendingFlows -= offlowmod.getBufferId
        }

      }
      case _ => throw new Exception("unrecognized OFFlowMod command type:" + offlowmod.getCommand)
    }
  }

  override def handleMessage(msg: OFMessage) {
    super.handleMessage(msg)
    msg.getType match {
      case OFType.FLOW_MOD_1 => processFlowMod(msg.asInstanceOf[OFFlowMod])
      case _ => {}
    }
  }

  startRateController()
}