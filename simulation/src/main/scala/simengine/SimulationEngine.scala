package scalasem.simengine

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable
import scala.concurrent.Lock

import scalasem.util.Logging


object SimulationEngine extends Logging {

  val queueReadingLock = new Lock
  var currentTime : Double = 0.0
  var reporter : Reporter = null

  //TODO:any more performant implementation?
  private var eventqueue : ArrayBuffer[Event] =
    new ArrayBuffer[Event] with mutable.SynchronizedBuffer[Event]
  private var numPassedEvents = 0

  var startTime: Double = 0.0
  var endTime: Double = 1000.0

  def run () {
    //setup periodical events
    PeriodicalEventManager.run(startTime, endTime, 10)
    while (!eventqueue.isEmpty) {
      queueReadingLock.acquire()
      logDebug("acquire lock at SimulationEngine")
      val event = eventqueue.head
      queueReadingLock.release()
      if (event.getTimeStamp() > endTime) return
      logDebug("release lock at SimulationEngine")
      if (event.getTimeStamp < currentTime) {
        throw new Exception("cannot execute an event happened before, event timestamp: " +
          event.getTimeStamp + ", currentTime:" + currentTime)
      }
      currentTime = event.getTimeStamp()
      //every event should be atomic
      event.process()
      numPassedEvents += 1
      eventqueue -= event
    }
    logTrace("Finished all events")
  }

  def summary() {
    if (reporter != null)
      reporter.report()
  }

  def Events() = eventqueue

  def numFinishedEvents = numPassedEvents

  def addEvent(e : Event) {
    this.synchronized {
      eventqueue += e
      eventqueue = eventqueue.sortWith(_.getTimeStamp < _.getTimeStamp)
    }
  }

  def contains(e : Event) = eventqueue.contains(e)

  def cancelEvent(e : Event) {
    if (eventqueue.contains(e)) {
      eventqueue -= e
    }
  }

  def reschedule(e : Event, time : Double) {
    if (time < currentTime) throw new Exception("cannot reschedule a event to the before")
    cancelEvent(e)
    e.setTimeStamp(time)
    addEvent(e)
  }

  def reset () {
    currentTime = 0.0
    numPassedEvents = 0
    eventqueue.clear()
  }
}



