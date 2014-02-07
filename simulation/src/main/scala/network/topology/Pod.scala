package network.topology

import scalasim.network.component.builder.{AddressInstaller}
import simengine.utils.{Logging, XmlParser}
import network.forwarding.controlplane.openflow.OpenFlowControlPlane
;

class Pod (private [topology] val cellID : Int,
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
        for (i <- 0 until aggregateRouterNumber) aggContainer(i).connectTOController()
        //ToR routers
        for (i <- 0 until numRacks) torContainer(i).connectTOController()
        //waiting for controller to process the topology
        //Thread.sleep(1000)
      }
    }

    def assignIPtoRacks() {
      for (i <- 0 until rackNumber) {
        //assign ip to the TOR router
        AddressInstaller.assignIPAddress(torContainer(i), "10." + cellID + "." + i + ".1")
        //assign ip addresses to the hosts
        AddressInstaller.assignIPAddress(torContainer(i).ip_addr(0),
          2, hostsContainer, i * rackSize, (i + 1) * rackSize - 1)
      }
    }

    def assignIPtoAggLayer() {
      for (i <- 0 until aggregateRouterNumber) {
        AddressInstaller.assignIPAddress(aggContainer(i), "10." + cellID + "." + (rackNumber + i) + ".1")
        AddressInstaller.assignIPAddress(aggContainer(i).ip_addr(0),
          2, torContainer, 0, torContainer.size - 1)
      }
    }


    hostsContainer.create(rackNumber * rackSize)
    torContainer.create(rackNumber, ToRRouterType)
    aggContainer.create(aggregateRouterNumber, AggregateRouterType)

    //main part of buildNetwork
    assignIPtoRacks()
    assignIPtoAggLayer()
    initOFNetwork()
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



