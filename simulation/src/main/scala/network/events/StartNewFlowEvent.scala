package network.events

import org.openflow.protocol.OFMatch
import network.traffic.{GlobalFlowStore, Flow}
import network.topology.Host
import simengine.{EventOfTwoEntities, SimulationEngine}
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import network.utils.FlowReporter

/**
 *
 * @param flow the new start matchfield
 * @param host the invoker of the matchfield
 * @param timestamp the timestamp of the event
 */
final class StartNewFlowEvent (flow : Flow, host : Host, timestamp : Double)
  extends EventOfTwoEntities [Flow, Host] (flow, host, timestamp) {

  def process() {
    logTrace("start the flow " + flow + " at " + SimulationEngine.currentTime)
    //null in the last parameter means it's the first hop of the flow
    SimulationEngine.queueReadingLock.acquire()
    logDebug("global flow num:" + GlobalFlowStore.size)
    GlobalFlowStore.addFlow(flow)
    logDebug("global flow num:" + GlobalFlowStore.size)
    logDebug("acquire lock at StartEvent")
    FlowReporter.registerFlowStart(flow)
    host.controlplane.routing(host, flow,
      OFFlowTable.createMatchField(flow = flow),
      null)
  }
}
