package scalasem.network

import scala.collection.mutable.ListBuffer

import org.scalatest.FunSuite

import scalasem.network.component.builder.AddressInstaller
import scalasem.network.events.StartNewFlowEvent
import scalasem.network.topology._
import scalasem.network.topology.builder.LanBuilder
import scalasem.dummyTopology.Pod
import scalasem.network.traffic.{FlowRateOrdering, RunningFlow, CompletedFlow, Flow}
import scalasem.simengine.{SimulationEngine, SimulationRunner}
import scalasem.util.Logging

class ControlPlaneSuite extends FunSuite with Logging {

  test("flow can be routed within a rack") {
    SimulationRunner.reset
    val torrouter = new Router(ToRRouterType, GlobalDeviceManager.globalDeviceCounter)
    val rackservers = new HostContainer
    rackservers.create(2)
    AddressInstaller.assignIPAddress(torrouter, "10.0.0.1")
    AddressInstaller.assignIPAddress(torrouter.ip_addr(0), 2, rackservers, 0, rackservers.size - 1)
    AddressInstaller.assignMacAddress(torrouter, "00:00:00:00:00:00")
    AddressInstaller.assignMacAddress(rackservers(0), "00:00:00:00:00:01")
    AddressInstaller.assignMacAddress(rackservers(1), "00:00:00:00:00:02")
    LanBuilder.buildRack(torrouter, rackservers, 0, rackservers.size, 1000.0)
    GlobalDeviceManager.addNewNode("10.0.0.1", torrouter)
    GlobalDeviceManager.addNewNode("10.0.0.2", rackservers(0))
    GlobalDeviceManager.addNewNode("10.0.0.3", rackservers(1))
    val flow1 = Flow(rackservers(0).toString, rackservers(1).toString,
      rackservers(0).mac_addr(0), rackservers(1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(rackservers(1).toString, rackservers(0).toString,
      rackservers(1).mac_addr(0), rackservers(0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, rackservers(0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, rackservers(1), 0))
    SimulationEngine.run()
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
  }

  test("flow (flood) can be routed within a rack") {
    SimulationRunner.reset()
    val torrouter = new Router(ToRRouterType, GlobalDeviceManager.globalDeviceCounter)
    val rackservers = new HostContainer
    rackservers.create(2)
    AddressInstaller.assignIPAddress(torrouter, "10.0.0.1")
    AddressInstaller.assignIPAddress(torrouter.ip_addr(0), 2, rackservers, 0, rackservers.size - 1)
    AddressInstaller.assignMacAddress(torrouter, "00:00:00:00:00:00")
    AddressInstaller.assignMacAddress(rackservers(0), "00:00:00:00:00:01")
    AddressInstaller.assignMacAddress(rackservers(1), "00:00:00:00:00:02")
    LanBuilder.buildRack(torrouter, rackservers, 0, rackservers.size, 1000.0)
    GlobalDeviceManager.addNewNode("10.0.0.1", torrouter)
    GlobalDeviceManager.addNewNode("10.0.0.2", rackservers(0))
    GlobalDeviceManager.addNewNode("10.0.0.3", rackservers(1))
    val flow1 = Flow(rackservers(0).toString, rackservers(1).toString,
      rackservers(0).mac_addr(0), rackservers(1).mac_addr(0), fflag = true, appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, rackservers(0), 0))
    SimulationEngine.run()
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
    SimulationEngine.run()
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
}
