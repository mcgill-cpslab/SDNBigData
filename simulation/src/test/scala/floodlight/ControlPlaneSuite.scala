package floodlight

import org.scalatest.FunSuite
import scalasim.network.component.builder.{AddressInstaller}
import simengine.utils.{Logging, XmlParser}
import network.topology._
import network.traffic._
import simengine.SimulationEngine
import network.events.StartNewFlowEvent
import root.SimulationRunner
import scala.collection.mutable.ListBuffer

class ControlPlaneSuite extends FunSuite with Logging {

  test("flow can be routed within a rack") {
    SimulationRunner.reset
    val torrouter = new Router(ToRRouterType, GlobalDeviceManager.globaldevicecounter)
    val rackservers = new HostContainer
    rackservers.create(2)
    AddressInstaller.assignIPAddress(torrouter, "10.0.0.1")
    AddressInstaller.assignIPAddress(torrouter.ip_addr(0), 2, rackservers, 0, rackservers.size - 1)
    LanBuilder.buildRack(torrouter, rackservers, 0, rackservers.size - 1)
    val flow1 = Flow(rackservers(0).toString, rackservers(1).toString,
      rackservers(0).mac_addr(0), rackservers(1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(rackservers(1).toString, rackservers(0).toString,
      rackservers(1).mac_addr(0), rackservers(0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, rackservers(0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, rackservers(1), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
  }

  test("flow (flood) can be routed within a rack") {
    SimulationRunner.reset
    val torrouter = new Router(ToRRouterType, GlobalDeviceManager.globaldevicecounter)
    val rackservers = new HostContainer
    rackservers.create(2)
    AddressInstaller.assignIPAddress(torrouter, "10.0.0.1")
    AddressInstaller.assignIPAddress(torrouter.ip_addr(0), 2, rackservers, 0, rackservers.size - 1)
    LanBuilder.buildRack(torrouter, rackservers, 0, rackservers.size - 1)
    val flow1 = Flow(rackservers(0).toString, rackservers(1).toString,
      rackservers(0).mac_addr(0), rackservers(1).mac_addr(0), fflag = true, appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, rackservers(0), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
  }

  test("flow can be routed across racks") {
    SimulationRunner.reset
    val pod = new Pod(1, 2, 4, 20)
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(3, 1).toString, pod.getHost(2, 1).toString,
      pod.getHost(3, 1).mac_addr(0), pod.getHost(2, 1).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(3, 1), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
  }

  test("flow (flood) can be routed across racks") {
    SimulationRunner.reset
    val pod = new Pod(1, 1, 2, 2)
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), fflag = true, appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 0).toString, pod.getHost(1, 0).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(1, 0).mac_addr(0), fflag = true, appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 0), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
  }

  test ("flows in a collection can be ordered according to their rate or temprate") {
    SimulationEngine.reset
    var flowset = new ListBuffer[Flow]
    flowset += Flow("10.0.0.1", "10.0.0.2", "", "", appDataSize = 1000)
    flowset(0).status = RunningFlow
    flowset(0).changeRate(10)
    flowset += Flow("10.0.0.2", "10.0.0.3", "", "", appDataSize = 1000)
    flowset(1).status = RunningFlow
    flowset(1).changeRate(100)
    flowset += Flow("10.0.0.3", "10.0.0.4", "", "", appDataSize = 1000)
    flowset(2).status = RunningFlow
    flowset(2).changeRate(20)
    flowset = flowset.sorted(FlowRateOrdering)
    assert(flowset(0).SrcIP === "10.0.0.1")
    assert(flowset(1).SrcIP === "10.0.0.3")
    assert(flowset(2).SrcIP === "10.0.0.2")
  }


  test("flow can be allocated with correct bandwidth (within the same rack)") {
    logInfo("flow can be allocated with correct bandwidth (within the same rack)")
    val pod = new Pod(1, 0, 1, 2)
    SimulationRunner.reset
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run
    assert(flow1.LastCheckPoint === 0.02)
    assert(flow2.LastCheckPoint === 0.02)
  }

  test("flow (flood) can be allocated with correct bandwidth (within the same rack)") {
    logInfo("flow (flood) can be allocated with correct bandwidth (within the same rack)")
    val pod = new Pod(1, 0, 1, 4)
    SimulationRunner.reset
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), fflag = true, appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), fflag = true, appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run
    assert(flow1.LastCheckPoint === 0.02)
    assert(flow2.LastCheckPoint === 0.02)
  }


  test("flow can be allocated with correct bandwidth (within the agg router) (case 1, one-one pattern)") {
    logInfo("flow can be allocated with correct bandwidth (within the agg router) (case 1)")
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({if (i == 0) 1 else 0}, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({if (i == 0) 1 else 0}, j).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run
    for (flow <- flowlist) {
      assert(flow.LastCheckPoint === 0.02)
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router) (case 1, one-one pattern)") {
    logInfo("flow can be allocated with correct bandwidth (within the agg router) (case 1)")
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({if (i == 0) 1 else 0}, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({if (i == 0) 1 else 0}, j).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run
    for (flow <- flowlist) {
      assert(flow.LastCheckPoint === 0.02)
    }
  }

  test("flow can be allocated with correct bandwidth (within the agg router) (case 2)") {
    SimulationRunner.reset
    logInfo("start flow can be allocated with correct bandwidth (within the agg router) " +
      "(when a flow decrease its rate, the other should take the margin) ")
    XmlParser.loadConf("config.xml")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "75.0")
    val pod = new Pod(1, 1, 2, 3)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 3) {
      val flow = Flow(pod.getHost(0, 0).toString, pod.getHost(1, i).toString,
        pod.getHost(0, 0).mac_addr(0), pod.getHost(1, i).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(0, 0), 0))
    }
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), appDataSize = 7.5)
    flowlist += flow1
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.run
    for (i <- 0 until flowlist.size) {
      if (i != flowlist.size - 1) assert(flowlist(i).LastCheckPoint === 0.04)
      else {
        println(flowlist(i))
        assert(BigDecimal(flowlist(i).LastCheckPoint).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
          === 0.11)
      }
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router) (case 2)") {
    logInfo("start flow (flood) can be allocated with correct bandwidth (within the agg router) " +
      "(when a flow decrease its rate, the other should take the margin) ")
    XmlParser.loadConf("config.xml")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "75.0")
    val pod = new Pod(1, 1, 2, 3)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 3) {
      val flow = Flow(pod.getHost(0, 0).toString, pod.getHost(1, i).toString,
        pod.getHost(0, 0).mac_addr(0), pod.getHost(1, i).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(0, 0), 0))
    }
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), fflag = true, appDataSize = 7.5)
    flowlist += flow1
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.run
    for (i <- 0 until flowlist.size) {
      if (i != flowlist.size - 1) assert(flowlist(i).LastCheckPoint === 0.04)
      else {
        println(flowlist(i))
        assert(BigDecimal(flowlist(i).LastCheckPoint).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
          === 0.11)
      }
    }
  }


  test("flow can be allocated with correct bandwidth (within the agg router, and agg link is congested)") {
    SimulationRunner.reset
    logInfo("flow can be allocated with correct bandwidth (within the agg router, and agg link is congested)")
    XmlParser.loadConf("config.xml")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "100.0")
    XmlParser.addProperties("scalasim.topology.crossrouterlinkrate", "100.0")
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({if (i == 1) 0 else 1}, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({if (i == 1) 0 else 1}, j).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run
    for (i <- 0 until flowlist.size) {
      assert(flowlist(i).LastCheckPoint === 0.08)
    }
  }

  test("flow (flood) can be allocated with correct bandwidth (within the agg router, and agg link is congested)") {
    SimulationRunner.reset
    logInfo("flow (flood) can be allocated with correct bandwidth " +
      "(within the agg router, and agg link is congested)")
    XmlParser.loadConf("config.xml")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "100.0")
    XmlParser.addProperties("scalasim.topology.crossrouterlinkrate", "100.0")
    val pod = new Pod(1, 1, 2, 4)
    val flowlist = new ListBuffer[Flow]
    SimulationRunner.reset
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({if (i == 1) 0 else 1}, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({if (i == 1) 0 else 1}, j).mac_addr(0), fflag = true, appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run
    for (i <- 0 until flowlist.size) {
      assert(flowlist(i).LastCheckPoint === 0.08)
    }
  }
}
