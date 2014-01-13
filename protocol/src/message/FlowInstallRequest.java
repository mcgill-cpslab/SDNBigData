package message;


import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallRequest extends AppAgentMsg {

  private int sourceIP = 0;
  private int destinationIP = 0;
  private short sourcePort = 0;
  private short destinationPort = 0;

  private int jobid = 0;
  private int jobpriority = 0;

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
    this.jobid = buffer.readInt();
    this.jobpriority = buffer.readInt();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeInt(sourceIP);
    data.writeInt(destinationIP);
    data.writeShort(sourcePort);
    data.writeShort(destinationPort);
    data.writeInt(jobid);
    data.writeInt(jobpriority);
  }

  public int getSourceIP() {
    return sourceIP;
  }

  public int getDestinationIP() {
    return destinationIP;
  }

  public int getDeadline() {
    return jobid;
  }

  public int getFlowsize() {
    return jobpriority;
  }


}