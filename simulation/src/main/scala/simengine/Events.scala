package scalasem.simengine

import scalasem.util.Logging

abstract class Event (protected var timestamp : Double) extends Logging {

  def setTimeStamp(t : Double) {
    timestamp = t
  }

  def getTimeStamp() = timestamp
  def process()
  def repeatInFuture(step : Double) : Event = null//empty
}

abstract class EventOfSingleEntity[EntityType] (val entity : EntityType, ts : Double) extends Event(ts)

abstract class EventOfTwoEntities[ET1, ET2] (val e1 : ET1, val e2 : ET2 , ts : Double) extends Event(ts)