package floodlight

import org.scalatest.FunSuite
import root.SimulationRunner
import network.topology._
import scalasim.network.component.builder.AddressInstaller
import simengine.utils.XmlParser
import org.openflow.util.{U32, HexString}
import utils.IPAddressConvertor
import org.openflow.protocol.factory.BasicFactory
import network.forwarding.controlplane.openflow.flowtable.OFFlowTable
import org.openflow.protocol.{OFFlowMod, OFType, OFMatch}
import org.openflow.protocol.action.{OFAction, OFActionOutput}
import network.forwarding.controlplane.openflow.{OFMatchField, OpenFlowControlPlane}
import java.util
import simengine.SimulationEngine
import network.traffic.{CompletedFlow, Flow}
import network.events.StartNewFlowEvent
import scala.collection.mutable.ListBuffer


class OpenFlowSuite extends FunSuite {

  test ("routers can be assigned with DPID address correctly") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    val aggrouter = new Router(AggregateRouterType, GlobalDeviceManager.globaldevicecounter)
    val torrouter = new Router(ToRRouterType, GlobalDeviceManager.globaldevicecounter)
    AddressInstaller.assignIPAddress(aggrouter, "10.1.0.1")
    AddressInstaller.assignIPAddress(torrouter, "10.1.0.2")
    assert(aggrouter.DPID === HexString.toLong("01:01:" + aggrouter.mac_addr(0)))
    assert(torrouter.DPID === HexString.toLong("00:01:" + torrouter.mac_addr(0)))
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
    val node = new Router(AggregateRouterType, GlobalDeviceManager.globaldevicecounter)
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
    val generatedmatchfield = OFFlowTable.createMatchField(flow, 0)
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.2")))
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.1")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~OFMatch.OFPFW_DL_VLAN
      & ~OFMatch.OFPFW_DL_SRC
      & ~OFMatch.OFPFW_DL_DST
      & ~OFMatch.OFPFW_NW_SRC_MASK
      & ~OFMatch.OFPFW_NW_DST_MASK)
    matchfield.setDataLayerVirtualLan(0)
    assert(matchfield.matching(generatedmatchfield) === true)
  }

  test ("flow table can match flow entry correctly (with ip mask)") {
    SimulationRunner.reset
    val matchfield = new OFMatchField
    val flow = Flow("10.0.0.1", "10.0.0.2", "00:00:00:00:00:11", "00:00:00:00:00:22", appDataSize = 1)
    val generatedmatchfield = OFFlowTable.createMatchField(flow, 0)
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.3")))
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.4")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~OFMatch.OFPFW_DL_VLAN
      & ~OFMatch.OFPFW_DL_SRC
      & ~OFMatch.OFPFW_DL_DST
      & ~(39 << OFMatch.OFPFW_NW_DST_SHIFT)
      & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT)
    )
    matchfield.setDataLayerVirtualLan(0)
    assert(matchfield.matching(generatedmatchfield) === true)
  }

  test ("OFMatchField hashCode testing") {
    SimulationRunner.reset
    val matchfield = new OFMatchField
    val flow = Flow("10.0.0.1", "10.0.0.2", "00:00:00:00:00:11", "00:00:00:00:00:22", appDataSize = 1)
    val generatedmatchfield = OFFlowTable.createMatchField(flow, 0)
    generatedmatchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~OFMatch.OFPFW_DL_VLAN
      & ~OFMatch.OFPFW_DL_SRC
      & ~OFMatch.OFPFW_DL_DST
      & ~(39 << OFMatch.OFPFW_NW_DST_SHIFT)
      & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT))
    //set matchfield
    matchfield.setInputPort(2)
    matchfield.setDataLayerDestination("00:00:00:00:00:22")
    matchfield.setDataLayerSource("00:00:00:00:00:11")
    matchfield.setNetworkSource(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.3")))
    matchfield.setNetworkDestination(U32.t(IPAddressConvertor.DecimalStringToInt("10.0.0.4")))
    matchfield.setWildcards(OFMatch.OFPFW_ALL
      & ~OFMatch.OFPFW_DL_VLAN
      & ~OFMatch.OFPFW_DL_SRC
      & ~OFMatch.OFPFW_DL_DST
      & ~(39 << OFMatch.OFPFW_NW_DST_SHIFT)
      & ~(39 << OFMatch.OFPFW_NW_SRC_SHIFT)
    )
    matchfield.setDataLayerVirtualLan(0)
    assert(matchfield.hashCode === generatedmatchfield.hashCode)
  }



  test("flow can be routed within a rack (openflow)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    val pod = new Pod(0, 1, 1, 20)
    Thread.sleep(1000 * 20)
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be routed across racks (openflow)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    val pod = new Pod(1, 2, 4, 20)
    val flow1 = Flow(pod.getHost(0, 1).toString, pod.getHost(1, 1).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(1, 1).mac_addr(0), appDataSize = 1)
    Thread.sleep(1000 * 20)
    val flow2 = Flow(pod.getHost(3, 1).toString, pod.getHost(2, 1).toString,
      pod.getHost(3, 1).mac_addr(0), pod.getHost(2, 1).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 1), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(3, 1), 0))
    SimulationEngine.run
    assert(flow1.status === CompletedFlow)
    assert(flow2.status === CompletedFlow)
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be allocated with correct bandwidth (within the same rack) (openflow)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    val pod = new Pod(1, 0, 1, 2)
    Thread.sleep(1000 * 20)
    val flow1 = Flow(pod.getHost(0, 0).toString, pod.getHost(0, 1).toString,
      pod.getHost(0, 0).mac_addr(0), pod.getHost(0, 1).mac_addr(0), appDataSize = 1)
    val flow2 = Flow(pod.getHost(0, 1).toString, pod.getHost(0, 0).toString,
      pod.getHost(0, 1).mac_addr(0), pod.getHost(0, 0).mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1, pod.getHost(0, 0), 0))
    SimulationEngine.addEvent(new StartNewFlowEvent(flow2, pod.getHost(0, 1), 0))
    SimulationEngine.run
    assert(flow1.LastCheckPoint === 0.02)
    assert(flow2.LastCheckPoint === 0.02)
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be allocated with correct bandwidth (within the agg router) " +
    "(case 1, one-one pattern)(openflow)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    val pod = new Pod(1, 1, 2, 4)
    Thread.sleep(1000 * 20)
    val flowlist = new ListBuffer[Flow]
    for (i <- 0 until 2; j <- 0 until 4) {
      val flow = Flow(pod.getHost(i, j).toString, pod.getHost({if (i == 0) 1 else 0}, j).toString,
        pod.getHost(i, j).mac_addr(0), pod.getHost({if (i == 0) 1 else 0}, j).mac_addr(0), appDataSize = 1)
      flowlist += flow
      SimulationEngine.addEvent(new StartNewFlowEvent(flow, pod.getHost(i, j), 0))
    }
    SimulationEngine.run
    flowlist.foreach(flow => assert(flow.LastCheckPoint === 0.02))
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be allocated with correct bandwidth (within the agg router) (case 2)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "75.0")
    val pod = new Pod(1, 1, 2, 3)
    Thread.sleep(1000 * 20)
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
        assert(BigDecimal(flowlist(i).LastCheckPoint).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
          === 0.11)
      }
    }
    pod.shutDownOpenFlowNetwork()
  }

  test("flow can be allocated with correct bandwidth (within the agg router, and agg link is congested)") {
    SimulationRunner.reset
    XmlParser.addProperties("scalasim.simengine.model", "openflow")
    XmlParser.addProperties("scalasim.topology.locallinkrate", "100.0")
    XmlParser.addProperties("scalasim.topology.crossrouterlinkrate", "100.0")
    val pod = new Pod(1, 1, 2, 4)
    Thread.sleep(1000 * 20)
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
    pod.shutDownOpenFlowNetwork()
  }
}
