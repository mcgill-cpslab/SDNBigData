package org.openflow.protocol;


import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFAction;

public class OFFlowMod1 extends OFFlowMod {

  public static int MINIMUM_LENGTH = 80;//


  protected long reqtype;
  protected int jobid;
  protected int value;

  public OFFlowMod1() {
    super();
    type = OFType.FLOW_MOD_1;
  }


  public int getJobid() {
    return jobid;
  }

  public void setJobid(int jobid) {
    this.jobid = jobid;
  }

  public long getReqtype() {
    return reqtype;
  }

  public void setReqtype(long t) {
    this.reqtype = t;
  }

  public int getReqvalue() {
    return value;
  }

  public void setReqvalue(int v) {
    this.value = v;
  }

  @Override
  public int hashCode() {
    final int prime = 227;
    int result = super.hashCode();
    result = prime * result + ((actions == null) ? 0 : actions.hashCode());
    result = prime * result + bufferId;
    result = prime * result + command;
    result = prime * result + (int) (cookie ^ (cookie >>> 32));
    result = prime * result + flags;
    result = prime * result + hardTimeout;
    result = prime * result + idleTimeout;
    result = prime * result + ((match == null) ? 0 : match.hashCode());
    result = prime * result + outPort;
    result = prime * result + priority;
    result = prime * result + (int) reqtype;
    result = prime * result + jobid;
    result = prime * result + value;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj)) return false;
    OFFlowMod1 flowMod1 = (OFFlowMod1) obj;
    if (reqtype != flowMod1.getReqtype()) return false;
    if (jobid != flowMod1.getJobid()) return false;
    if (value != flowMod1.getReqvalue()) return false;
    return true;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#clone()
   */
  @Override
  public OFFlowMod clone() throws CloneNotSupportedException {
    OFFlowMod1 flowMod1= (OFFlowMod1) super.clone();
    flowMod1.setJobid(jobid);
    flowMod1.setReqvalue(value);
    flowMod1.setReqtype(reqtype);
    return flowMod1;
  }

  @Override
  public void readFrom(ChannelBuffer data) {
    super.readFrom(data);
    this.reqtype = data.readLong();
    this.jobid = data.readInt();
    this.value = data.readInt();
  }

  @Override
  public void writeTo(ChannelBuffer data) {
    super.writeTo(data);
    data.writeLong(reqtype);
    data.writeInt(jobid);
    data.writeInt(value);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return super.toString() + "," + jobid + "," + reqtype + "," + value;
  }
}
