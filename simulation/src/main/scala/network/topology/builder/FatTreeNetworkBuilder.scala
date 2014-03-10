package scalasem.network.topology.builder

import scalasem.network.topology._
import scalasem.network.component.builder.AddressInstaller
import scalasem.util.XmlParser

class FatTreeBuilder (private val podnum: Int = 4,
                      private val linkspeed: Double = 1.0) {

  val pods = 0 until podnum
  val core_sws_idx = 1 until (podnum / 2  + 1)
  val agg_sws_idx = podnum / 2 until podnum
  val edge_sws_idx = 0 until (podnum / 2)
  val hosts_idx = 2 until (podnum / 2 + 2)

  def buildNetwork(coreRouters: RouterContainer, aggRouters : RouterContainer,
                   edgeRouters : RouterContainer,
                   hosts: HostContainer) {
    val cellsegment = pods.foldLeft(-1)((a, b) => Math.max(a, b)) + 1
    for (pod_idx <- pods) {
      //connect the eage to each aggregate switch and make the connection within the rack
      for (edge_idx <- edge_sws_idx) {
        val edgeRouterGID = pod_idx * podnum / 2 + edge_idx
        edgeRouters(edgeRouterGID).id_gen(pod_idx, edge_idx, 1)
        //assign ip to the TOR router
        AddressInstaller.assignIPAddress(edgeRouters(edgeRouterGID),
          "10." + pod_idx + "." + edge_idx + ".1")
        AddressInstaller.assignMacAddress(edgeRouters(edgeRouterGID),
          s"00:00:00:$pod_idx:$edge_idx:1")
        GlobalDeviceManager.addNewNode(edgeRouters(edgeRouterGID).ip_addr(0),
          edgeRouters(edgeRouterGID))
        //assign ip address to hosts
        AddressInstaller.assignIPAddress(
          edgeRouters(edgeRouterGID).ip_addr(0),
          2, hosts, edgeRouterGID * podnum / 2,
          (edgeRouterGID + 1) * podnum / 2 - 1)
        for (host_idx <- hosts_idx) {
          val hostGID = edgeRouterGID * podnum / 2 + host_idx - 2
          //assign ip addresses to the hosts
          hosts(hostGID).id_gen(pod_idx, edge_idx, host_idx)
          AddressInstaller.assignMacAddress(hosts(hostGID),
            s"00:00:00:$pod_idx:$edge_idx:$host_idx")
          val newlink = new Link(hosts(hostGID), edgeRouters(edgeRouterGID), linkspeed)
          hosts(hostGID).interfacesManager.registerOutgoingLink(newlink)
          edgeRouters(edgeRouterGID).interfacesManager.registerIncomeLink(newlink)
          GlobalDeviceManager.addNewNode(hosts(hostGID).ip_addr(0), hosts(hostGID))
        }

        for (agg_idx <- agg_sws_idx) {
          val aggRouterGID = pod_idx * podnum / 2 + agg_idx - podnum / 2
          AddressInstaller.assignIPAddress(
            aggRouters(aggRouterGID),
            "10." + pod_idx + "." + agg_idx + ".1")
          AddressInstaller.assignMacAddress(aggRouters(aggRouterGID),
            s"00:00:00:$pod_idx:$agg_idx:1")
          aggRouters(aggRouterGID).id_gen(pod_idx, agg_idx, 1)
          val newlink = new Link(edgeRouters(edgeRouterGID), aggRouters(aggRouterGID), linkspeed)
          aggRouters(aggRouterGID).interfacesManager.registerIncomeLink(newlink)
          edgeRouters(edgeRouterGID).interfacesManager.registerOutgoingLink(newlink)
          GlobalDeviceManager.addNewNode(aggRouters(aggRouterGID).ip_addr(0),
            aggRouters(aggRouterGID))
        }
      }

      for (agg_idx <- agg_sws_idx) {
        val aggRouterGID = pod_idx * podnum / 2 + agg_idx - podnum / 2
        val c_index = agg_idx - podnum / 2 + 1
        for (core_idx <- core_sws_idx) {
          val coreRouterGID = (agg_idx - podnum / 2) * podnum / 2 + core_idx - 1
          //assign ip address to core switches
          AddressInstaller.assignIPAddress(coreRouters(coreRouterGID), "10." + cellsegment +
            "."  + c_index + "." + core_idx)
          AddressInstaller.assignMacAddress(coreRouters(coreRouterGID),
            s"00:00:00:$podnum:$c_index:$core_idx")
          coreRouters(coreRouterGID).id_gen(podnum, c_index, core_idx)
          val newlink = new Link(aggRouters(aggRouterGID), coreRouters(coreRouterGID),
            linkspeed)
          aggRouters(aggRouterGID).interfacesManager.registerOutgoingLink(newlink)
          coreRouters(coreRouterGID).interfacesManager.registerIncomeLink(newlink)
          GlobalDeviceManager.addNewNode(coreRouters(coreRouterGID).ip_addr(0),
            coreRouters(coreRouterGID))
        }
      }
    }
  }
}


object FatTreeNetworkBuilder {

  val coreRouters: RouterContainer = new RouterContainer
  val aggregateRouters: RouterContainer = new RouterContainer
  val edgeRouters: RouterContainer = new RouterContainer
  val hosts: HostContainer = new HostContainer

  var k: Int = -1

  def initNetwork() {
    coreRouters.create(k * k / 4, CoreRouterType)
    aggregateRouters.create(k * k / 2 , AggregateRouterType)
    edgeRouters.create(k * k / 2, ToRRouterType)
    hosts.create(k * k / 2 * k / 2)
  }

  def buildFatTreeNetwork(linkspeed: Double) {
    val ftbuilder = new FatTreeBuilder(k, linkspeed)
    ftbuilder.buildNetwork(coreRouters, aggregateRouters, edgeRouters, hosts)
  }

  def initOFNetwork() {
    if (XmlParser.getString("scalasim.simengine.model", "tcp") == "openflow") {
      val connectioninterval = XmlParser.getInt("scalasim.simengine.connectioninterval", 500)
      //CORE ROUTERS
      for (i <- 0 until coreRouters.size()) {
        coreRouters(i).connectTOController()
        Thread.sleep(connectioninterval)
      }
      //aggeregate routers
      for (i <- 0 until aggregateRouters.size()) {
        aggregateRouters(i).connectTOController()
        Thread.sleep(connectioninterval)
      }
      //ToR routers
      for (i <- 0 until edgeRouters.size()) {
        edgeRouters(i).connectTOController()
        Thread.sleep(connectioninterval)
      }
    }
  }

  def getAllHosts = hosts
}
