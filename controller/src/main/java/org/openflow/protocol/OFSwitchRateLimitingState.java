package org.openflow.protocol;

import org.openflow.util.U16;

/**
 * the message sent from the switches to the controller
 */
public class OFSwitchRateLimitingState extends OFMessage {


  private int tablesize;

  public OFSwitchRateLimitingState() {
    super();
    this.type = OFType.SWITCH_RATE_LIMITING_STATE;
    this.length = U16.t(OFMessage.MINIMUM_LENGTH + 4);
  }

  public int getTablesize() {
    return tablesize;
  }
}
