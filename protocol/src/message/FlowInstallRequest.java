package message;


import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallRequest extends AppAgentMsg {


  private int sourceIP = 0;
  private int destinationIP = 0;
  private short sourcePort = 0;

  private short destinationPort = 0;

  private int idx = 0;
  private int reqtype = 0;
  private long value = 0;

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
    this.reqtype = buffer.readInt();
    this.value = buffer.readLong();
    this.idx = buffer.readInt();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeInt(sourceIP);
    data.writeInt(destinationIP);
    data.writeShort(sourcePort);
    data.writeShort(destinationPort);
    data.writeInt(reqtype);
    data.writeLong(value);
    data.writeInt(idx);
  }

  public int getSourceIP() {
    return sourceIP;
  }

  public int getDestinationIP() {
    return destinationIP;
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

  public void setReqtype(int reqtype) {
    this.reqtype = reqtype;
  }

  public void setValue(long v) {
    value = v;
  }

  public long getValue() {
    return value;
  }

  public short getSourcePort() {
    return sourcePort;
  }

  public short getDestinationPort() {
    return destinationPort;
  }

  public int getIdx() {
    return idx;
  }

  public void setIdx(int idx) {
    this.idx = idx;
  }
}