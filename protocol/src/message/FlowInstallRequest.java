package message;


import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallRequest extends AppAgentMsg {


  private int sourceIP = 0;
  private int destinationIP = 0;
  private short sourcePort = 0;

  private short destinationPort = 0;

  private int jobid = 0;
  private int jobpriority = 0;

  private int idx = 0;

  public FlowInstallRequest() {
    super();
    type = AppMsgType.FLOW_INSTALL_REQUEST;
    length += 24;
  }

  public void readFrom(ChannelBuffer buffer) {
    super.readFrom(buffer);
    this.sourceIP = buffer.readInt();
    this.destinationIP = buffer.readInt();
    this.sourcePort = buffer.readShort();
    this.destinationPort = buffer.readShort();
    this.jobid = buffer.readInt();
    this.jobpriority = buffer.readInt();
    this.idx = buffer.readInt();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeInt(sourceIP);
    data.writeInt(destinationIP);
    data.writeShort(sourcePort);
    data.writeShort(destinationPort);
    data.writeInt(jobid);
    data.writeInt(jobpriority);
    data.writeInt(idx);
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

  public int getIdx() {
    return idx;
  }

  public void setIdx(int idx) {
    this.idx = idx;
  }

  public void setSourceIP(int sourceIP) {
    this.sourceIP = sourceIP;
  }

  public void setDestinationIP(int destinationIP) {
    this.destinationIP = destinationIP;
  }

  public void setSourcePort(short sourcePort) {
    this.sourcePort = sourcePort;
  }

  public void setDestinationPort(short destinationPort) {
    this.destinationPort = destinationPort;
  }

  public void setJobid(int jobid) {
    this.jobid = jobid;
  }

  public void setJobpriority(int jobpriority) {
    this.jobpriority = jobpriority;
  }

  public int getJobid() {
    return jobid;
  }

  public int getJobpriority() {
    return jobpriority;
  }

  public short getSourcePort() {
    return sourcePort;
  }

  public short getDestinationPort() {
    return destinationPort;
  }
}