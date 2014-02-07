package root

import network.topology.{Host, Core, Pod, GlobalDeviceManager}
import simengine.{PeriodicalEventManager, SimulationEngine}
import _root_.simengine.utils.XmlParser
import application.ApplicationRunner
import network.utils.FlowReporter
import network.events.{StartNewFlowEvent, UpdateFlowPropertyEvent}
import network.topology.builder.FatTreeNetworkBuilder
import network.traffic.Flow


object SimulationRunner {

  def reset() {
    GlobalDeviceManager.globaldevicecounter = 0
    SimulationEngine.reset()
    ApplicationRunner.reset()
    XmlParser.reset()
  }

  def main(args:Array[String]) = {
    XmlParser.loadConf(args(0))

    val startime = System.currentTimeMillis()

    FatTreeNetworkBuilder.k = XmlParser.getInt("scalasim.topo.fatree.podnum", 4)
    FatTreeNetworkBuilder.initNetwork()
    FatTreeNetworkBuilder.buildFatTreeNetwork(1000.0)
    FatTreeNetworkBuilder.initOFNetwork()
    println("Warming up...")
    Thread.sleep(XmlParser.getInt("scalasim.app.warmingtime", 2))

    ApplicationRunner.setResource(FatTreeNetworkBuilder.getAllHosts)
    ApplicationRunner.installApplication()
    ApplicationRunner.run()
    /*val flow1 = Flow(GlobalDeviceManager.getNode("10.0.0.2").toString,
      GlobalDeviceManager.getNode("10.3.1.2").toString,
      GlobalDeviceManager.getNode("10.0.0.2").mac_addr(0),
      GlobalDeviceManager.getNode("10.3.1.2").mac_addr(0), appDataSize = 1)
    SimulationEngine.addEvent(new StartNewFlowEvent(flow1,
      GlobalDeviceManager.getNode("10.0.0.2").asInstanceOf[Host], 0))*/
    //SimulationEngine.run
   // PeriodicalEventManager.event = new UpdateFlowPropertyEvent(0)
    SimulationEngine.startTime = 0.0
    SimulationEngine.endTime = 10000.0
    SimulationEngine.reporter = FlowReporter
    SimulationEngine.run()
    SimulationEngine.summary()

    println("time cost:" + (System.currentTimeMillis() - startime))
  }
}


