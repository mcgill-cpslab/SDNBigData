package scalasem.network.events

import scala.util.Random

import scalasem.simengine.{SimulationEngine, EventOfSingleEntity}
import scalasem.network.traffic.{CompletedFlow, Flow}
import scalasem.application.OnOffApp
import scalasem.network.topology.GlobalDeviceManager
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.util.Logging


class FlowOnEvent (flow : Flow, timestamp : Double)
  extends EventOfSingleEntity[Flow] (flow, timestamp) with Logging {

  def process {
    if (flow.status != CompletedFlow) {
      logTrace(flow + " is on")
      //scheduler off event
      val nextOffMoment = Random.nextInt(OnOffApp.offLength)
      SimulationEngine.addEvent(new FlowOffEvent(flow,
        SimulationEngine.currentTime + nextOffMoment))
      flow.changeRate(Double.MaxValue)
      //reallocate resources
      GlobalDeviceManager.getNode(flow.srcIP).dataplane.reallocate(
        GlobalDeviceManager.getNode(flow.dstIP), //destination host
        flow, //offflow
        OFFlowTable.createMatchField(flow = flow)) //matchfield
    }
  }
}
