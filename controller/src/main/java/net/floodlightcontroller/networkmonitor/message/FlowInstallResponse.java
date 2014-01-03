package net.floodlightcontroller.networkmonitor.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallResponse extends AppAgentMsg {

  private byte installedSuccessfully = 0;


  public FlowInstallResponse() {
    super();
    length += 1;
    type = AppMsgType.FLOW_INSTALL_RESPONSE;
  }

  public void readFrom(ChannelBuffer buffer) {
    super.readFrom(buffer);
    installedSuccessfully = buffer.readByte();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeByte(installedSuccessfully);
  }

  public byte isInstalledSuccessfully() {
    return installedSuccessfully;
  }

  public void setInstalledSuccessfully(byte installedSuccessfully) {
    this.installedSuccessfully = installedSuccessfully;
  }

}
