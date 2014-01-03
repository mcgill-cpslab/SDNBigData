package org.openflow.protocol;


import org.openflow.util.U16;

/**
 * send from agent to the controller
 */
public class OFFlowInstallRequest extends OFMessage {

  private OFMatch flowMatchingRule;
  private int deadline;
  private long size;

  public OFFlowInstallRequest() {
    super();
    this.type = OFType.FLOW_INSTALL_REQUEST;
    this.length = U16.t(8 + 4 + OFMatch.MINIMUM_LENGTH);
  }

  public OFMatch getFlowMatchingRule() {
    return flowMatchingRule;
  }

  public void setFlowMatchingRule(OFMatch flowMatchingRule) {
    this.flowMatchingRule = flowMatchingRule;
  }

  public int getDeadline() {
    return deadline;
  }

  public void setDeadline(int deadline) {
    this.deadline = deadline;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }
}
