package scalasem.network.events

import scalasem.network.traffic.{GlobalFlowStore, Flow}
import scalasem.simengine.{EventOfSingleEntity, SimulationEngine}
import scalasem.network.topology.GlobalDeviceManager
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.network.utils.FlowReporter
import scalasem.util.Logging

/**
 *
 * @param flow finished matchfield
 * @param t timestamp of the event
 */
final class CompleteFlowEvent (flow : Flow, t : Double)
  extends EventOfSingleEntity[Flow] (flow, t) with Logging {

  def process() {
    logInfo("flow " + flow + " completed at " + SimulationEngine.currentTime)
    flow.close()
    FlowReporter.registerFlowEnd(flow)
    //ends at the flow destination
    val matchfield = OFFlowTable.createMatchField(flow = flow)
    GlobalDeviceManager.getNode(flow.dstIP).dataplane.finishFlow(
      GlobalDeviceManager.getNode(flow.dstIP), flow, matchfield)
    GlobalFlowStore.removeFlow(flow)
  }
}
