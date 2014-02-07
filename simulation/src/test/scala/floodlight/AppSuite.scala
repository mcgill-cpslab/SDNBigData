package floodlight

import org.scalatest.FunSuite
import simengine.utils.XmlParser
import simengine.SimulationEngine
import network.topology.Pod
import application.{PermuMatrixApp, ApplicationRunner}

class AppSuite extends FunSuite {

  test ("PermuMatrixApp can build the matrix correctly") {
    XmlParser.loadConf("config.xml")
    val cellnet = new Pod(1)
    SimulationEngine.reset()
    ApplicationRunner.reset()
    ApplicationRunner.setResource(cellnet.getAllHostsInPod)
    ApplicationRunner.installApplication()
    ApplicationRunner.run()
    assert(ApplicationRunner("PermuMatrixApp").asInstanceOf[PermuMatrixApp].selectedPairSize ===
      cellnet.numMachinesPerRack * cellnet.numRacks)
  }
}
