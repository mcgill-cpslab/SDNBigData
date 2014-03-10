package scalasem.network

import scala.collection.mutable.ListBuffer

import org.scalatest.FunSuite

import scalasem.network.events.StartNewFlowEvent
import scalasem.dummyTopology.Pod
import scalasem.network.traffic.Flow
import scalasem.simengine.{SimulationEngine, SimulationRunner}
import scalasem.util.{XmlParser, Logging}

class DataPlaneSuite extends FunSuite with Logging {

  test("flow can be allocated with correct bandwidth (within the same rack)") {
    SimulationRunner.reset()
    val pod = new Pod(1, 1, 1, 2)
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run()
    assert(flow1.LastCheckPoint === 0.002)
    assert(flow2.LastCheckPoint === 0.002)
  }

  test("flow (flood) can be allocated with correct bandwidth (within the same rack)") {
    val pod = new Pod(1, 1, 1, 4)
    SimulationRunner.reset()
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), fflag = true, appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), fflag = true, appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run()
    assert(flow1.LastCheckPoint === 0.002)
    assert(flow2.LastCheckPoint === 0.002)
  }


  test("flow can be allocated with correct bandwidth (within the agg router) " +
    "(case 1, one-one pattern)") {
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset()
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({
        if (i == 0) 1 else 0
      }, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({
          if (i == 0) 1 else 0
        }, j).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run()
    for (flow <- flowlist) {
      assert(flow.LastCheckPoint === 0.008)
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router) " +
    "(case 1, one-one pattern)") {
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset()
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({
        if (i == 0) 1 else 0
      }, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({
          if (i == 0) 1 else 0
        }, j).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run()
    for (flow <- flowlist) {
      assert(flow.LastCheckPoint === 0.008)
    }
  }

  test("flow can be allocated with correct bandwidth (within the agg router) (case 2)") {
    SimulationRunner.reset()
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    for (i <- 0 until 3) {
      val flow = Flow(pod.getHost(0, 0).toString, pod.getHost(1, i).toString,
        pod.getHost(0, 0).mac_addr(0), pod.getHost(1, i).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(0, 0), 0))
    }
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 3).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 3).mac_addr(0), appDataSize = 2)
    flowlist += flow1
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.run()
    for (i <- 0 until flowlist.size) {
      if (i != flowlist.size - 1) assert(flowlist(i).LastCheckPoint === 0.004)
      else {
        assert(flowlist(i).LastCheckPoint === 0.005)
      }
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router) (case 2)") {
    SimulationRunner.reset()
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    for (i <- 0 until 3) {
      val flow = Flow(pod.getHost(0, 0).toString, pod.getHost(1, i).toString,
        pod.getHost(0, 0).mac_addr(0), pod.getHost(1, i).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(0, 0), 0))
    }
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 3).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 3).mac_addr(0), fflag = true, appDataSize = 2)
    flowlist += flow1
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.run()
    for (i <- 0 until flowlist.size) {
      if (i != flowlist.size - 1) assert(flowlist(i).LastCheckPoint === 0.004)
      else {
        assert(flowlist(i).LastCheckPoint === 0.005)
      }
    }
  }


  test("flow can be allocated with correct bandwidth " +
    "(within the agg router, and agg link is congested)") {
    SimulationRunner.reset()
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({
        if (i == 1) 0 else 1
      }, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({
          if (i == 1) 0 else 1
        }, j).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run()
    for (i <- 0 until flowlist.size) {
      assert(flowlist(i).LastCheckPoint === 0.008)
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router, " +
    "and agg link is congested)") {
    SimulationRunner.reset()
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset()
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({
        if (i == 1) 0 else 1
      }, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({
          if (i == 1) 0 else 1
        }, j).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run()
    for (i <- 0 until flowlist.size) {
      assert(flowlist(i).LastCheckPoint === 0.008)
    }
  }
}
