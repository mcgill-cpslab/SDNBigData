package scalasem.dummyTopology

import scalasem.network.component.builder.AddressInstaller
import scalasem.network.topology._
import scalasem.util.{Logging, XmlParser}

// A class representing the pod with 1g links
class Pod (private[dummyTopology] val podID : Int,
           private val aggregateRouterNumber : Int = XmlParser.getInt("scalasim.topology.cell.aggregaterouternum", 2),
           private val rackNumber : Int = XmlParser.getInt("scalasim.topology.cell.racknum", 4),
           private val rackSize : Int = XmlParser.getInt("scalasim.topology.cell.racksize", 20))
  extends Logging {

  private val aggContainer = new RouterContainer
  private val torContainer = new RouterContainer
  private val hostsContainer = new HostContainer

  private def buildNetwork() {
    def initOFNetwork() {
      if (XmlParser.getString("scalasim.simengine.model", "tcp") == "openflow") {
        //aggeregate routers
        for (i <- 0 until aggregateRouterNumber) {
          aggContainer(i).id_gen(podID, i, 1)
          aggContainer(i).connectTOController()
        }
        //ToR routers
        for (i <- 0 until numRacks) {
          torContainer(i).id_gen(podID, i + aggregateRouterNumber, 1)
          torContainer(i).connectTOController()
        }
        //waiting for controller to process the topology
        //Thread.sleep(1000)
      }
    }

    def assignMACtoRacks() {
      for (i <- 0 until aggregateRouterNumber;
           j <- aggregateRouterNumber until aggregateRouterNumber + rackNumber;
           k <- 0 until rackSize ) {
        //assign mac to agg router
        if (aggContainer(i).mac_addr.size <= 0) {
          AddressInstaller.assignMacAddress(
            aggContainer(i),
            s"00:00:00:$podID:$i:1")
        }
        //assign mac to the TOR router
        if (torContainer(j - aggregateRouterNumber).mac_addr.size <= 0) {
          AddressInstaller.assignMacAddress(
            torContainer(j - aggregateRouterNumber),
            s"00:00:00:$podID:$j:1")
        }
        AddressInstaller.assignMacAddress(
          hostsContainer((j - aggregateRouterNumber) * rackSize + k),
          s"00:00:00:$podID:$j:$k")
      }
    }

    def assignIPtoRacks() {
      for (i <- 0 until rackNumber) {
        //assign ip to the TOR router
        AddressInstaller.assignIPAddress(torContainer(i), "10." + podID + "." + i + ".1")
        //assign ip addresses to the hosts
        AddressInstaller.assignIPAddress(torContainer(i).ip_addr(0),
          2, hostsContainer, i * rackSize, (i + 1) * rackSize - 1)
      }
    }

    def assignIPtoAggLayer() {
      for (i <- 0 until aggregateRouterNumber) {
        AddressInstaller.assignIPAddress(aggContainer(i),
          "10." + podID + "." + (rackNumber + i) + ".1")
        AddressInstaller.assignIPAddress(aggContainer(i).ip_addr(0),
          2, torContainer, 0, torContainer.size - 1)
      }
    }

    def buildTopology() {
      for (i <- 0 until aggregateRouterNumber) {
        for (j <- 0 until rackNumber) {
          val torToAggLink = new Link(torContainer(j), aggContainer(i), 1000.0)
          torContainer(j).interfacesManager.registerOutgoingLink(torToAggLink)
          aggContainer(i).interfacesManager.registerIncomeLink(torToAggLink)
        }
      }
      for (j <- 0 until rackNumber) {
        for (k <- 0 until rackSize) {
          val serverToTorLink = new Link(hostsContainer(j * rackSize + k), torContainer(j),
            1000.0)
          hostsContainer(j * rackSize + k).interfacesManager.registerOutgoingLink(
            serverToTorLink)
          torContainer(j).interfacesManager.registerIncomeLink(serverToTorLink)
        }
      }
    }

    def globalDevicesRegistration() {
      //register all hosts
      for (i <- 0 until hostsContainer.size()) {
        GlobalDeviceManager.addNewNode(hostsContainer(i).ip_addr(0), hostsContainer(i))
      }
      for (i <- 0 until torContainer.size()) {
        GlobalDeviceManager.addNewNode(torContainer(i).ip_addr(0), torContainer(i))
      }
      for (i <- 0 until aggContainer.size()) {
        GlobalDeviceManager.addNewNode(aggContainer(i).ip_addr(0), aggContainer(i))
      }
    }

    hostsContainer.create(rackNumber * rackSize)
    torContainer.create(rackNumber, ToRRouterType)
    aggContainer.create(aggregateRouterNumber, AggregateRouterType)

    //main part of buildNetwork
    assignMACtoRacks()
    assignIPtoRacks()
    assignIPtoAggLayer()
    initOFNetwork()
    buildTopology()
    globalDevicesRegistration()
  }

  def shutDownOpenFlowNetwork() {
    //aggeregate routers
    for (i <- 0 until aggregateRouterNumber) aggContainer(i).disconnectFromController()
    //ToR routers
    for (i <- 0 until numRacks) torContainer(i).disconnectFromController()
  }

  def numAggRouters = aggregateRouterNumber
  def numRacks = rackNumber
  def numMachinesPerRack = rackSize

  def getAggregatRouter(idx : Int) : Router = aggContainer(idx)
  def getToRRouter(idx : Int) : Router = torContainer(idx)
  def getHost(rackID : Int, hostID : Int) : Host = hostsContainer(rackID * rackSize + hostID)

  def getAllAggRouters: RouterContainer = aggContainer
  def getAllToRRouters: RouterContainer = torContainer
  def getAllHostsInPod : HostContainer = hostsContainer

  buildNetwork()
}