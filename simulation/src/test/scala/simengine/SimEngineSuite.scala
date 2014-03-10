package scalasem.simengine

import scala.collection.mutable.ListBuffer

import org.scalatest.FunSuite

class SimEngineSuite extends FunSuite{
  class DummySingleEntity (entity : String, t : Double) extends EventOfSingleEntity[String] (entity, t) {
    def process() {
      if (entity == "e1") {
        SimulationEngine.addEvent(new DummySingleEntity("e4", 15))
      }
      DummySingleEntity.finishedStamp += this.timestamp
    }
    override def toString() = entity
  }

  object DummySingleEntity {
    val finishedStamp = new ListBuffer[Double]
    def reset() = finishedStamp.clear
  }

  test ("Events should be ordered with their timestamp") {
    SimulationRunner.reset
    DummySingleEntity.reset
    val e1 = new DummySingleEntity("e1", 2)
    val e2 = new DummySingleEntity("e2", 1)
    val e3 = new DummySingleEntity("e3", 0)
    SimulationEngine.addEvent(e1)
    SimulationEngine.addEvent(e2)
    SimulationEngine.addEvent(e3)
    SimulationEngine.run
    var r = -1.0
    assert(DummySingleEntity.finishedStamp.size === 4)
    for (e <- DummySingleEntity.finishedStamp) {
      assert((r <= e) === true)
      r = e
    }
  }

  test ("Events can be rescheduled") {
    SimulationRunner.reset
    DummySingleEntity.reset
    val e1 = new DummySingleEntity("e1", 10)
    val e2 = new DummySingleEntity("e2", 5)
    val e3 = new DummySingleEntity("e3", 20)
    SimulationEngine.addEvent(e1)
    SimulationEngine.addEvent(e2)
    SimulationEngine.addEvent(e3)
    SimulationEngine.reschedule(e3, 1)
    SimulationEngine.reschedule(e3, 2)
    SimulationEngine.reschedule(e3, 3)
    assert(SimulationEngine.contains(e3) === true)
    assert(SimulationEngine.Events.size === 3)
    assert(SimulationEngine.Events.head === e3)
  }

  test ("eventqueue can be dynamically modified") {
    SimulationRunner.reset
    DummySingleEntity.reset
    val e1 = new DummySingleEntity("e1", 10)
    val e2 = new DummySingleEntity("e2", 5)
    val e3 = new DummySingleEntity("e3", 20)
    SimulationEngine.addEvent(e1)
    SimulationEngine.addEvent(e2)
    SimulationEngine.addEvent(e3)
    SimulationEngine.run
    assert(SimulationEngine.numFinishedEvents === 4)
  }

  test ("SimEngine can update the current system time") {
    SimulationRunner.reset
    DummySingleEntity.reset
    val e1 = new DummySingleEntity("e1", 10)
    val e2 = new DummySingleEntity("e2", 5)
    val e3 = new DummySingleEntity("e3", 20)
    SimulationEngine.addEvent(e1)
    SimulationEngine.addEvent(e2)
    SimulationEngine.addEvent(e3)
    SimulationEngine.run
    assert(SimulationEngine.currentTime === 20)
  }
}
