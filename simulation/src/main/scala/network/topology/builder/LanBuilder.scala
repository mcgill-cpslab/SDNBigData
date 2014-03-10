package scalasem.network.topology.builder

import scalasem.network.topology.{RouterContainer, Link, HostContainer, Router}

object LanBuilder {

  def buildRack(torRouter: Router, servers: HostContainer, startIdx: Int, endIdx: Int,
                 bandwidth: Double) {
    for (i <- startIdx until endIdx) {
      val server = servers(i)
      val link = new Link(server, torRouter, bandwidth)
      torRouter.interfacesManager.registerIncomeLink(link)
      server.interfacesManager.registerOutgoingLink(link)
    }
  }

  def buildPod(torRouter: Router, routers: RouterContainer, startIdx: Int, endIdx: Int,
                bandwidth: Double) {
    for (i <- startIdx until endIdx) {
      val router = routers(i)
      val link = new Link(router, torRouter, bandwidth)
      torRouter.interfacesManager.registerIncomeLink(link)
      router.interfacesManager.registerOutgoingLink(link)
    }
  }
}
