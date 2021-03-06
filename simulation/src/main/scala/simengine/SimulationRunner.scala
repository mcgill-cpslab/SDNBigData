package scalasem.simengine

import scalasem.application.ApplicationRunner
import scalasem.network.topology.builder.{FatTreeNetworkBuilder, Pod}
import scalasem.network.topology.GlobalDeviceManager
import scalasem.network.utils.FlowReporter
import scalasem.util.XmlParser


object SimulationRunner {

  def reset() {
    GlobalDeviceManager.globalDeviceCounter = 0
    SimulationEngine.reset()
    ApplicationRunner.reset()
    XmlParser.reset()
  }

  def main(args:Array[String]) = {
    XmlParser.loadConf(args(0))
    val startime = System.currentTimeMillis()

    FatTreeNetworkBuilder.k = XmlParser.getInt("scalasim.topo.fatree.podnum", 4)
    println("Initializing Network...")
    FatTreeNetworkBuilder.initNetwork()
    println("Building FatTree Network...")
    FatTreeNetworkBuilder.buildFatTreeNetwork(1000.0)
    println("Finished Building Fat Tree Network...")
    FatTreeNetworkBuilder.initOFNetwork()
    println("Warming up...")
    Thread.sleep(XmlParser.getInt("scalasim.app.warmingtime", 20))


    ApplicationRunner.setResource(FatTreeNetworkBuilder.getAllHosts)
    ApplicationRunner.installApplication()
    ApplicationRunner.run()
    SimulationEngine.startTime = 0.0
    SimulationEngine.endTime = 1000.0
    SimulationEngine.reporter = FlowReporter
    SimulationEngine.run()
    SimulationEngine.summary()

    println("time cost:" + (System.currentTimeMillis() - startime))
  }
}


