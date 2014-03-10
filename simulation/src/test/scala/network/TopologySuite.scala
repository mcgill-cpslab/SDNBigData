package scalasem.network

import org.scalatest.FunSuite

import scalasem.network.component.builder.AddressInstaller
import scalasem.network.topology._
import scalasem.network.topology.builder.LanBuilder
import scalasem.simengine.SimulationRunner
import scalasem.dummyTopology.Pod

class TopologySuite extends FunSuite {

  test("AddressInstaller can assign IPs to a host/router") {
     val host : Host = new Host(HostType, GlobalDeviceManager.globalDeviceCounter)
     SimulationRunner.reset
     AddressInstaller.assignIPAddress(host, "10.0.0.1")
     assert(host.ip_addr.length == 1 && host.ip_addr(0) == "10.0.0.1")
     AddressInstaller.assignIPAddress(host, "10.0.0.2")
     assert(host.ip_addr.length == 2 && host.ip_addr(0) == "10.0.0.1" &&
       host.ip_addr(1) == "10.0.0.2")
  }

  test("AddressInstaller can assign IPs to host/router container") {
    SimulationRunner.reset
    val hostContainer : HostContainer = new HostContainer()
    val router : Router = new Router(ToRRouterType, GlobalDeviceManager.globalDeviceCounter)
    AddressInstaller.assignIPAddress(router, "10.0.0.1")
    hostContainer.create(10)
    AddressInstaller.assignIPAddress(router.ip_addr(router.ip_addr.length - 1), 2,
      hostContainer, 0, 9)
    for (i <- 0 until 10) {
      assert(hostContainer(i).ip_addr(0) === "10.0.0." + (i + 2))
    }
  }

  test("LanBuilder cannot build the lan for router-hosts before all " +
    "involved elements are assigned with IP addresses") {
    SimulationRunner.reset
    val router : Router = new Router(ToRRouterType, GlobalDeviceManager.globalDeviceCounter)
    val hostContainer = new  HostContainer()
    hostContainer.create(10)
    var exception = intercept[RuntimeException] {
      LanBuilder.buildRack(router, hostContainer, 0, 9, 1000.0)
    }
    router.assignIP("10.0.0.1")
    exception = intercept[RuntimeException] {
      LanBuilder.buildRack(router, hostContainer, 0, 9, 1000.0)
    }
  }

  test("LanBuilder cannot build the lan for router-routers before " +
    "all involved elements are assigned with IP addresses") {
    SimulationRunner.reset
    val router : Router = new Router(AggregateRouterType, GlobalDeviceManager.globalDeviceCounter)
    val routerContainer = new RouterContainer()
    routerContainer.create(10, ToRRouterType)
    var exception = intercept[RuntimeException] {
      LanBuilder.buildPod(router, routerContainer, 0, 9, 1000.0)
    }
    router.assignIP("10.0.0.1")
    exception = intercept[RuntimeException] {
      LanBuilder.buildPod(router, routerContainer, 0, 9, 1000.0)
    }
  }

  test("LanBuilder should be able to create the local area network for router-hosts") {
    SimulationRunner.reset()
    val router : Router = new Router(ToRRouterType, GlobalDeviceManager.globalDeviceCounter)
    val hosts : HostContainer = new HostContainer()
    AddressInstaller.assignIPAddress(router, "10.0.0.1")
    hosts.create(10)
    AddressInstaller.assignIPAddress(router.ip_addr(0), 2, hosts, 0, 9)
    LanBuilder.buildRack(router, hosts, 0, 10, 1000.0)
    for (i <- 0 to 9) {
      val hostOutLink = hosts(i).interfacesManager.getOutLinks("10.0.0.1")
      //check host outlink
      assert(router.ip_addr(0) === hostOutLink.get.end_to.ip_addr(0))
      //check router inlink
      assert(router.interfacesManager.getInLinks(hosts(i).ip_addr(0)).get.end_from === hosts(i))
    }
  }

  test("LanBuilder should be able to create the local area network for router-routers") {
    SimulationRunner.reset
    val aggRouter : Router = new Router(AggregateRouterType,
      GlobalDeviceManager.globalDeviceCounter)
    val routers : RouterContainer = new RouterContainer()
    AddressInstaller.assignIPAddress(aggRouter, "10.0.0.1")
    routers.create(10, ToRRouterType)
    AddressInstaller.assignIPAddress(aggRouter.ip_addr(0), 2, routers, 0, 9)
    LanBuilder.buildPod(aggRouter, routers, 0, 10, 1000.0)
    for (i <- 0 to 9) {
      val routerOutlink = routers(i).interfacesManager.getOutLinks("10.0.0.1")
      //check tor routers outlink
      assert(aggRouter.ip_addr(0) === routerOutlink.get.end_to.ip_addr(0))
      //check aggregate router inlink
      assert(aggRouter.interfacesManager.getInLinks(routers(i).ip_addr(0)).get.end_from ===
        routers(i))
    }
  }

  test("Pod network can be created correctly") {
    SimulationRunner.reset
    val cellnet = new Pod(1)
    //check aggregate routers's inlinks
    for (i <- 0 until cellnet.numAggRouters; j <- 0 until cellnet.numRacks) {
      val aggrouter = cellnet.getAggregatRouter(i)
      assert(aggrouter.interfacesManager.getInLinks("10.1." + j + ".1") != None)
    }
    //check tor router's inlinks and outlinks
    for (i <- 0 until cellnet.numRacks; j <- 0 until cellnet.numMachinesPerRack) {
      val torrouter = cellnet.getToRRouter(i)
      //check inlinks
      assert(torrouter.interfacesManager.getInLinks("10.1." + i + "." + (j + 2)) != None)
    }
    for (i <- 0 until cellnet.numRacks; j <- 0 until cellnet.numAggRouters) {
      val torrouter = cellnet.getToRRouter(i)
      //check outlink
      assert(torrouter.interfacesManager.getOutLinks(
        "10.1." + (cellnet.numRacks  + j).toString + ".1") != None)
    }
    //check hosts outlinks
    for (i <- 0 until cellnet.numRacks; j <- 0 until cellnet.numMachinesPerRack) {
      assert(cellnet.getHost(i, j).interfacesManager.getOutLinks("10.1." + i + ".1") != None)
    }
  }
}
