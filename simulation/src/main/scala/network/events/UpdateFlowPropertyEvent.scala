package scalasem.network.events

import scalasem.simengine.Event
import scalasem.network.traffic.GlobalFlowStore
import scalasem.network.topology.GlobalDeviceManager
import scalasem.network.forwarding.controlplane.openflow.OpenFlowControlPlane

class UpdateFlowPropertyEvent (timestamp : Double) extends Event(timestamp) {

  override def repeatInFuture(future : Double) : Event =
    new UpdateFlowPropertyEvent(timestamp + future)

  def process() {

    logTrace("update flow properties")

    GlobalFlowStore.getFlows.foreach(f => f.updateTransferredData())
    //we assume all routers are openflow-enabled
    GlobalDeviceManager.getAllRouters.foreach(router =>
      router.controlplane.asInstanceOf[OpenFlowControlPlane].sendFlowCounters())
    //flush buffer
    GlobalDeviceManager.getAllRouters.foreach(router => {
      val ofplane = router.controlplane.asInstanceOf[OpenFlowControlPlane]
      ofplane.ofmsgsender.flushBuffer(ofplane.toControllerChannel)
    })
  }
}
