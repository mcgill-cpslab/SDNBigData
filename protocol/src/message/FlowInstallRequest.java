package message;


import org.jboss.netty.buffer.ChannelBuffer;

public class FlowInstallRequest extends AppAgentMsg {


  private int sourceIP = 0;
  private int destinationIP = 0;
  private short sourcePort = 0;

  private short destinationPort = 0;

  private int idx = 0;
  private long reqtype = 0;
  private int value = 0;

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
    this.reqtype = buffer.readLong();
    this.value = buffer.readInt();
    this.idx = buffer.readInt();
  }

  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeInt(sourceIP);
    data.writeInt(destinationIP);
    data.writeShort(sourcePort);
    data.writeShort(destinationPort);
    data.writeLong(reqtype);
    data.writeInt(value);
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

  public void setReqtype(long reqtype) {
    this.reqtype = reqtype;
  }

  public long getReqtype() {return this.reqtype;}

  public void setValue(int v) {
    value = v;
  }

  public int getValue() {
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