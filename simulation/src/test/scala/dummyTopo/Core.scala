package scalasem.dummyTopology

import scalasem.network.component.builder.AddressInstaller
import scalasem.network.topology.{CoreRouterType, RouterContainer}

class Core (private val coreRouterNumber : Int,
             pods : Pod *) {

  private val coreContainer = new RouterContainer

  private val aggContainer = new RouterContainer

  def init() {
    pods.foreach(pod => {
      for (i <- 0 until pod.numAggRouters)
        aggContainer.addNode(pod.getAggregatRouter(i))
    })
    coreContainer.create(coreRouterNumber, CoreRouterType)
  }

  def assignIPtoCoreLayer() {
    val cellsegment = pods.foldLeft(-1)((a, b) => Math.max(a, b.podID)) + 1
    for (i <- 0 until coreContainer.size) {
      AddressInstaller.assignIPAddress(coreContainer(i), "10." + cellsegment + "."  + i + ".1")
      AddressInstaller.assignIPAddress(coreContainer(i).ip_addr(0),
        2, aggContainer, 0, aggContainer.size - 1)
    }
  }

  def getAllCoreSwitches = coreContainer


  init()
  assignIPtoCoreLayer()
 // buildCoreNetwork()
}
