package scalasem.network.utils

import scalasem.simengine.{SimulationEngine, Reporter}
import scalasem.network.traffic.Flow
import scala.collection.mutable


object FlowReporter extends  Reporter {

  private val flowStore = new mutable.HashMap[Flow, Tuple2[Double, Double]]

  def registerFlowStart(flow : Flow) {
    assert(!flowStore.contains(flow))
    flowStore.put(flow, new Tuple2(SimulationEngine.currentTime, 0))
  }

  def registerFlowEnd(flow : Flow) {
    assert(flowStore.contains(flow))
    flowStore.put(flow,
      new Tuple2(flowStore.get(flow).get._1, SimulationEngine.currentTime))
  }

  def report() {
    val str = new StringBuffer()
    flowStore.foreach(flowrecord => {
      str.append(flowrecord._1.toString() + "\t" + flowrecord._2._1 + "\t" + flowrecord._2._2 + "\n")
    })
    println(str.toString)
  }
}
