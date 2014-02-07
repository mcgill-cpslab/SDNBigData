package network.events

import network.traffic.{CompletedFlow, Flow}
import simengine.{SimulationEngine, EventOfSingleEntity}
import application.OnOffApp
import scala.util.Random
import network.topology.GlobalDeviceManager
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import org.openflow.protocol.OFMatch
import simengine.utils.Logging

class FlowOffEvent (flow : Flow, timestamp : Double)
  extends EventOfSingleEntity[Flow] (flow, timestamp) with Logging {

  def process {
    if (flow.getEgressLink == null) return//TODO:for duplicate flow, to be removed
    logTrace(flow + " is off")
    if (flow.status != CompletedFlow) {
      flow.changeRate(0)
      if (flow.AppDataSize > 0) {
        //SCHEDULE next ON event
        val nextOnMoment = Random.nextInt(OnOffApp.onLength)
        SimulationEngine.addEvent(new FlowOnEvent(flow,
          SimulationEngine.currentTime + nextOnMoment))
        //reallocate resources
        GlobalDeviceManager.getNode(flow.srcIP).dataplane.reallocate(
          GlobalDeviceManager.getNode(flow.dstIP), //destination host
          flow, //offflow
          OFFlowTable.createMatchField(flow = flow)) //matchfield
      }
    }
  }
}
