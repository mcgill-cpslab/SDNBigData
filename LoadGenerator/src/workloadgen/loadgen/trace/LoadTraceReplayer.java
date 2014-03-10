package workloadgen.loadgen.trace;

import workloadgen.loadgen.LoadSubmissionPlan;
import workloadgen.loadgen.LoadTraceGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LoadTraceReplayer extends LoadTraceGenerator {

  private String tracePath = null;
  private LoadSubmissionPlan subPlan = null;

  public LoadTraceReplayer(String tPath) {
    this.tracePath = tPath;
    this.subPlan = new LoadSubmissionPlan();
  }

  @Override
  public LoadSubmissionPlan getSubmissionPlan() {
    parse();
    return subPlan;
  }

  private void parse() {
    BufferedReader in;
    Pattern submitRecordPattern = Pattern.compile(
            "^(.+)\t([0-9]+)\t([0-9]+)\t([0-9]+)\t(.+)\t(.+)\t([0-9]+)\t([0-9]+)$");
    try {
      in = new BufferedReader(new FileReader(tracePath));
      String s;
      while ((s = in.readLine()) != null) {
        Matcher matcher = submitRecordPattern.matcher(s);
        if (matcher.find()) {
          for (int i = 0; i < Integer.parseInt(matcher.group(3)); i++) {
            LoadSubmissionPlan.LoadSubmissionPoint subPoint =
                    subPlan.new LoadSubmissionPoint(
                            matcher.group(1),//jobtype
                            Integer.parseInt(matcher.group(2)),//submit time
                            Integer.parseInt(matcher.group(3)),//num of jobs
                            Integer.parseInt(matcher.group(4)),//num of reducers
                            matcher.group(5),//input Size
                            matcher.group(6),//queueName
                            Integer.parseInt(matcher.group(7)),//reqtype
                            Long.parseLong(matcher.group(8)));//reqvalue
            subPlan.addNewPoint(subPoint);
          }
        }
      }
      in.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
