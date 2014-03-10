package scalasem.network


import java.util

import org.scalatest.FunSuite
import org.openflow.util.{U32, HexString}
import org.openflow.protocol.factory.BasicFactory
import org.openflow.protocol.{OFFlowMod, OFType, OFMatch}
import org.openflow.protocol.action.{OFAction, OFActionOutput}

import _root_.utils.IPAddressConvertor
import scalasem.dummyTopology.Pod
import scalasem.network.component.builder.AddressInstaller
import scalasem.network.events.StartNewFlowEvent
import scalasem.network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import scalasem.network.forwarding.controlplane.openflow.{OFMatchField, OpenFlowControlPlane}
import scalasem.network.topology._
import scalasem.network.traffic.{CompletedFlow, Flow}
import scalasem.simengine.{SimulationEngine, SimulationRunner}
import scalasem.util.{Logging, XmlParser}


class OpenFlowSuite extends FunSuite with Logging {

  test ("routers can be assigned with DPID address correctly") {
    SimulationRunner.reset
    val aggrouter = new Router(AggregateRouterType, GlobalDeviceManager.globalDeviceCounter)
    aggrouter.id_gen(1, 1, 1)
    assert(aggrouter.DPID === 65793)
  }

  test ("decimal dot ip address can be translated into integer correctly") {
    SimulationRunner.reset
    assert(U32.t(IPAddressConvertor.DecimalStringToInt("192.168.1.1")) === 0xC0A80101)
    assert(U32.t(IPAddressConvertor.DecimalStringToInt("10.4.4.1")) === 0xA040401)
    assert(U32.t(IPAddressConvertor.DecimalStringToInt("255.255.255.255")) === 0xFFFFFFFF)
  }

  test ("the integer can be translated into decimal dot ip address") {
    SimulationRunner.reset
    assert(IPAddressConvertor.IntToDecimalString(0xC0A80101) === "192.168.1.1")
    assert(IPAddressConvertor.IntToDecimalString(0xA040401) === "10.4.4.1")
    assert(IPAddressConvertor.IntToDecimalString(0xFFFFFFFF) === "255.255.255.255")
  }

  test ("when add flow table entry it can schedule entry expire event correctly") {
    SimulationRunner.reset
    val node = new Router(AggregateRouterType, GlobalDeviceManager.globalDeviceCounter)
    AddressInstaller.assignIPAddress(node, "10.0.0.1")
    val ofroutingmodule = new OpenFlowControlPlane(node)
    val offactory = new BasicFactory
    val table = new OFFlowTable(0, ofroutingmodule)
    val matchfield = new OFMatch
    val outaction  = new OFActionOutput
    val actionlist = new util.ArrayList[OFAction]
    actionlist.add(outaction)
    var flow_mod = offactory.getMessage(OFType.FLOW_MOD).asInstanceOf[OFFlowMod]
    flow_mod.setMatch(matchfield)
    flow_mod.setActions(actionlist)
    flow_mod.setCommand(OFFlowMod.OFPFC_ADD)
    flow_mod.setHardTimeout(500)
    flow_mod.setIdleTimeout(200)
    //first is in 200
    table.addFlowTableEntry(flow_mod)
    //second is in 500
    flow_mod = offactory.getMessage(OFType.FLOW_MOD).asInstanceOf[OFFlowMod]
    flow_mod.setMatch(matchfield)
    flow_mod.setActions(actionlist)
    flow_mod.setCommand(OFFlowMod.OFPFC_ADD)
    flow_mod.setHardTimeout(500)
    flow_mod.setIdleTimeout(0)
    table.addFlowTableEntry(flow_mod)
    //third is in 200
    flow_mod = offactory.getMessage(OFType.FLOW_MOD).asInstanceOf[OFFlowMod]
    flow_mod.setMatch(matchfield)
    flow_mod.setActions(actionlist)
    flow_mod.setCommand(OFFlowMod.OFPFC_ADD)
    flow_mod.setHardTimeout(0)
    flow_mod.setIdleTimeout(200)
    table.addFlowTableEntry(flow_mod)
    //forth is 100
    flow_mod = offactory.getMessage(OFType.FLOW_MOD).asInstanceOf[OFFlowMod]
    flow_mod.setMatch(matchfield)
    flow_mod.setActions(actionlist)
    flow_mod.setCommand(OFFlowMod.OFPFC_ADD)
    flow_mod.setHardTimeout(100)
    flow_mod.setIdleTimeout(200)
    table.addFlowTableEntry(flow_mod)
    assert(SimulationEngine.Events.length === 4)
    //sorted
    assert(SimulationEngine.Events.toList(0).getTimeStamp() === 100)
    assert(SimulationEngine.Events.toList(1).getTimeStamp() === 200)
    assert(SimulationEngine.Events.toList(2).getTimeStamp() === 200)
    assert(SimulationEngine.Events.toList(3).getTimeStamp() === 500)
  }

  test ("flow table can match flow entry correctly") {
    SimulationRunner.reset
    val matchfield = new OFMatchField
    val flow = Flow("10.0.0.1", "10.0.0.2", "00:00:00:00:00:11", "00:00:00:00:00:22", appDataSize = 1)
    val generatedmatchfield = OFFlowTable.createMatchField(flow)
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.2")))
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.1")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL & OFMatch.OFPFW_IN_PORT)
    matchfield.setDataLayerType(0x800)
    matchfield.setDataLayerVirtualLan(flow.vlanID)
    matchfield.setNetworkProtocol(6)
    assert(matchfield.matching(generatedmatchfield) === true)
  }

  test ("flow table can match flow entry correctly (with ip mask)") {
    SimulationRunner.reset()
    val matchfield = new OFMatchField
    val flow = Flow("10.0.0.1", "10.0.0.2", "00:00:00:00:00:11",
      "00:00:00:00:00:22", appDataSize = 1)
    val generatedmatchfield = OFFlowTable.createMatchField(flow, 0)
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.3")))
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.4")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL &
      ~(39 << OFMatch.OFPFW_NW_DST_SHIFT) & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT))
    matchfield.setDataLayerVirtualLan(flow.vlanID)
    matchfield.setDataLayerType(0x800)
    matchfield.setNetworkProtocol(6)
    assert(matchfield.matching(generatedmatchfield) === true)
  }

  test ("OFMatchField hashCode testing") {
    SimulationRunner.reset()
    val matchfield = new OFMatchField
    val flow = Flow("10.0.0.1", "10.0.0.2", "00:00:00:00:00:11", "00:00:00:00:00:22", appDataSize = 1)
    val generatedmatchfield = OFFlowTable.createMatchField(flow, 0)
    generatedmatchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~(39 << OFMatch.OFPFW_NW_DST_SHIFT)
      & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT))
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.3")))
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.4")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~(39 << OFMatch.OFPFW_NW_DST_SHIFT)
      & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT)
    )
    matchfield.setDataLayerVirtualLan(flow.vlanID)
    matchfield.setDataLayerType(0x800)
    matchfield.setNetworkProtocol(6)
    assert(matchfield.hashCode === generatedmatchfield.hashCode)
  }

  test("flow can be routed within a rack (openflow)") {
    SimulationRunner.reset()
    XmlParser.set("scalasim.simengine.model", "openflow")
    val pod = new Pod(1, 1, 1, 20)
    Thread.sleep(1000 * 20)
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run()
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be routed across racks (openflow)") {
    SimulationRunner.reset()
    logInfo("flow can be routed across racks (openflow)")
    XmlParser.set("scalasim.simengine.model", "openflow")
    val pod = new Pod(1, 2, 4, 4)
    Thread.sleep(1000 * 20)
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(3, 1).toString, pod.getHost(2, 1).toString,
      pod.getHost(3, 1).mac_addr(0), pod.getHost(2, 1).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(3, 1), 0))
    SimulationEngine.run()
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
    pod.shutDownOpenFlowNetwork()
  }
}
