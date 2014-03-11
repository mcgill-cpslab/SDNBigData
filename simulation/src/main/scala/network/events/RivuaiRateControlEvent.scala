package scalasem.network.events

import scalasem.simengine.EventOfSingleEntity
import scalasem.network.forwarding.dataplane.RivuaiDataPlane

class RivuaiRateControlEvent(dp: RivuaiDataPlane, ts: Double )
  extends EventOfSingleEntity[RivuaiDataPlane](dp, ts) {

  override def process() {
    dp.regulateFlow()
  }
}
