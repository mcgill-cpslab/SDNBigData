package org.openflow.protocol;

import org.openflow.util.U16;


/**
 * sent from the controller to the application agent
 */
public class OFFlowInstallResponse extends OFMessage {

  private boolean startFlag = false;

  public OFFlowInstallResponse() {
    super();
    this.type = OFType.FLOW_INSTALL_RESPONSE;
    this.length = U16.t(OFMessage.MINIMUM_LENGTH + 1);
  }

  public boolean isStartFlag() {
    return startFlag;
  }

  public void setStartFlag(boolean startFlag) {
    this.startFlag = startFlag;
  }
}
