package scalasem.application

import org.scalatest.FunSuite

import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser
import scalasem.dummyTopology.Pod

class AppSuite extends FunSuite {

  test ("PermuMatrixApp can build the matrix correctly") {
    XmlParser.set("application.application.names", "PermuMatrixApp")
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
