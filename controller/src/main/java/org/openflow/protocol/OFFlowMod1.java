package org.openflow.protocol;



public class OFFlowMod1 extends OFFlowMod {

  public static int MINIMUM_LENGTH = 80;//


  protected int jobid;
  protected int jobpriority;

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

  public int getJobpriority() {
    return jobpriority;
  }

  public void setJobpriority(int jobpriority) {
    this.jobpriority = jobpriority;
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
    result = prime * result + jobid;
    result = prime * result + jobpriority;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj)) return false;
    OFFlowMod1 flowMod1 = (OFFlowMod1) obj;
    if (jobid != flowMod1.getJobid()) return false;
    if (jobpriority != flowMod1.getJobpriority()) return false;
    return true;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#clone()
   */
  @Override
  public OFFlowMod clone() throws CloneNotSupportedException {
    OFFlowMod1 flowMod1= (OFFlowMod1) super.clone();
    flowMod1.setJobid(jobid);
    flowMod1.setJobpriority(jobpriority);
    return flowMod1;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return super.toString() + "," + jobid + "," + jobpriority;
  }
}
