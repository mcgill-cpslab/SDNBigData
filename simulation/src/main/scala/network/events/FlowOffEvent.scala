package scalasem.network.events

import scala.util.Random

import scalasem.network.traffic.{CompletedFlow, Flow}
import scalasem.simengine.{SimulationEngine, EventOfSingleEntity}
import scalasem.application.OnOffApp
import scalasem.network.topology.GlobalDeviceManager
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTableBase
import scalasem.util.Logging

class FlowOffEvent (flow : Flow, timestamp : Double)
  extends EventOfSingleEntity[Flow] (flow, timestamp) with Logging {

  def process() {
    if (flow.getEgressLink == null) return//TODO:for duplicate flow, to be removed
    logTrace(flow + " is off")
    if (flow.status != CompletedFlow) {
      flow.changeRate(0)
      if (flow.remainingAppData > 0) {
        //SCHEDULE next ON event
        val nextOnMoment = Random.nextInt(OnOffApp.onLength)
        SimulationEngine.addEvent(new FlowOnEvent(flow,
          SimulationEngine.currentTime + nextOnMoment))
        //reallocate resources
        GlobalDeviceManager.getNode(flow.srcIP).dataplane.reallocate(
          GlobalDeviceManager.getNode(flow.dstIP), //destination host
          flow, //offflow
          OFFlowTableBase.createMatchField(flow = flow)) //matchfield
      }
    }
  }
}
