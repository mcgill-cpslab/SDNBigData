package net.floodlightcontroller.networkmonitor.message;

import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallRequest extends AppAgentMsg {

  private int sourceIP = 0;
  private int destinationIP = 0;
  private short sourcePort = 0;
  private short destinationPort = 0;
  private long deadline = 0;
  private long flowsize = 0;

  public FlowInstallRequest() {
    super();
    type = AppMsgType.FLOW_INSTALL_REQUEST;
    length += 28;
  }

  public void readFrom(ChannelBuffer buffer) {
    super.readFrom(buffer);
    this.sourceIP = buffer.readInt();
    this.destinationIP = buffer.readInt();
    this.sourcePort = buffer.readShort();
    this.destinationPort = buffer.readShort();
    this.deadline = buffer.readLong();
    this.flowsize = buffer.readLong();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeInt(sourceIP);
    data.writeInt(destinationIP);
    data.writeShort(sourcePort);
    data.writeShort(destinationPort);
    data.writeLong(deadline);
    data.writeLong(flowsize);
  }
}
