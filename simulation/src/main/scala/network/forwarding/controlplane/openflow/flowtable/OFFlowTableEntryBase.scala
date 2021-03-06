package scalasem.network.forwarding.controlplane.openflow.flowtable

import scala.collection.mutable.ListBuffer

import org.openflow.protocol.OFMatch
import org.openflow.protocol.action.OFAction

import scalasem.simengine.SimulationEngine
import scalasem.network.events.OFFlowTableEntryExpireEvent
import scalasem.network.forwarding.controlplane.openflow.OFFlowCount

// implement OpenFlow 1.0 definition
class OFFlowTableEntryBase (private[openflow] val table: OFFlowTableBase) {
  protected[forwarding] var ofmatch : OFMatch = null
  protected[openflow] val counter : OFFlowCount = new OFFlowCount
  protected[controlplane] val actions : ListBuffer[OFAction] = new ListBuffer[OFAction]
  protected var lastAccessPoint : Int = SimulationEngine.currentTime.toInt
  protected[openflow] var flowHardExpireMoment : Int = 0
  protected[openflow] var flowIdleDuration : Int = 0
  protected[openflow] var priority : Short = 0
  protected[openflow] var expireEvent : OFFlowTableEntryExpireEvent = null

  def getLastAccessPoint = lastAccessPoint

  def refreshlastAccessPoint() {
    lastAccessPoint = SimulationEngine.currentTime.toInt
    determineEntryExpireMoment()
  }

  def Counters = counter

  private def determineEntryExpireMoment () {
    var expireMoment = 0
    val idleDuration = flowIdleDuration
    val idleexpireMoment = lastAccessPoint + flowIdleDuration
    val hardexpireMoment = flowHardExpireMoment
    if (
      (hardexpireMoment != 0 && idleDuration == 0 && expireEvent == null) ||
        (hardexpireMoment != 0 && idleexpireMoment != 0 && hardexpireMoment < idleexpireMoment)) {
      expireMoment = hardexpireMoment
    }
    else {
      if (
        (hardexpireMoment == 0 && idleDuration != 0) ||
          (hardexpireMoment != 0 && idleexpireMoment != 0 && idleexpireMoment < hardexpireMoment)) {
        expireMoment = idleexpireMoment
      }
    }
    if (expireMoment != 0) {
      expireEvent = new OFFlowTableEntryExpireEvent(table,
        OFFlowTableBase.createMatchFieldFromOFMatch(ofmatch),
        expireMoment)
      SimulationEngine.addEvent(expireEvent)
    }
  }
}
