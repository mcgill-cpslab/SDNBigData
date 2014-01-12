package org.openflow.protocol;

import org.openflow.util.U16;

/**
 * the message sent from the switches to the controller
 */
public class OFSwitchRateLimitingState extends OFMessage {


  private int tablesize;
  private int deadlinelowbound;
  private byte alldeadlinebound;

  public OFSwitchRateLimitingState() {
    super();
    this.type = OFType.SWITCH_RATE_LIMITING_STATE;
    this.length = U16.t(OFMessage.MINIMUM_LENGTH + 9);
  }

  public int getTablesize() {
    return tablesize;
  }

  public int getDeadlinelowbound() {
    return deadlinelowbound;
  }

  public byte getAlldeadlinebound() {return alldeadlinebound; }
}
