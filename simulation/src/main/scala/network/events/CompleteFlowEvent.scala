package network.events

import org.openflow.protocol.OFMatch
import network.traffic.{GlobalFlowStore, Flow}
import simengine.{EventOfSingleEntity, SimulationEngine}
import network.topology.GlobalDeviceManager
import simengine.utils.Logging
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import network.utils.FlowReporter

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
