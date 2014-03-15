package scalasem.network.forwarding.controlplane.openflow.flowtable


class OFRivuaiFlowTableEntry(table: OFFlowTableBase) extends OFFlowTableEntryBase(table) {
  private[forwarding] var jobid: Int = -1
  private[forwarding] var reqtype: Int = -1
  private[forwarding] var reqvalue: Double = -1.0
  private[forwarding] var ratelimit: Double = 0.0

  lazy val inportNum: Short = ofmatch.getInputPort

  // last timestamp when the flow is checked by Rivuai
  private[forwarding] var lastCheckpoint: Double = 0.0
}
