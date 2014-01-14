package message;

import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallResponse extends AppAgentMsg {

  private byte installedSuccessfully = 0;

  private int idx = 0;


  public FlowInstallResponse() {
    super();
    length += 5;
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


  public int getIdx() {
    return idx;
  }

  public void setIdx(int idx) {
    this.idx = idx;
  }


}