package workloadgen.loadgen;

import java.util.ArrayList;

/**
 * this class describe the trace of the workload
 */
public class LoadSubmissionPlan {

  enum LoadJobType {
    Grep,
    Sort,
    WebSort,
  }

  ;

  /**
   * this class describe each job_submission event in trace
   */
  public class LoadSubmissionPoint {

    private LoadJobType jobType;
    private int subTime;
    private int numOfJobs;
    private int numOfReduce;
    private String inputSize;
    private String queueName;
    private int reqtype;
    private long reqvalue;


    public LoadSubmissionPoint(String type, int timestamp, int numJobs, int numReduce, String isize,
                               String queue, int reqtype, long reqvalue) {
      this.subTime = timestamp;
      this.jobType = LoadJobType.valueOf(type);
      this.numOfJobs = numJobs;
      this.numOfReduce = numReduce;
      this.inputSize = isize;
      this.queueName = queue;
      this.reqtype = reqtype;
      this.reqvalue = reqvalue;
    }

    public String getJobType() {
      return this.jobType.toString();
    }

    public int getTimestamp() {
      return subTime;
    }

    public int getNumOfJobs() {
      return this.numOfJobs;
    }

    public int getNumReduce() {
      return this.numOfReduce;
    }

    public String getInputSize() {
      return this.inputSize;
    }

    public String getQueueName() {
      return this.queueName;
    }

    public int getReqtype() {
      return this.reqtype;
    }

    public long getReqvalue() {
      return this.reqvalue;
    }

    /**
     * for test
     */
    public void dump() {
      System.out.println("at " + subTime + " submit " + jobType);
    }
  }

  private ArrayList<LoadSubmissionPoint> list = null;

  public LoadSubmissionPlan() {
    list = new ArrayList<LoadSubmissionPoint>();
  }

  public void addNewPoint(LoadSubmissionPoint point) {
    list.add(point);
  }

  public ArrayList<LoadSubmissionPoint> getList() {
    return this.list;
  }

  /**
   * for test
   */
  public void dump() {
    for (int i = 0; i < list.size(); i++) {
      list.get(i).dump();
    }
  }
}
