package scalasem.application

import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.Random

import message.FlowInstallRequest
import utils.IPAddressConvertor

import scalasem.network.events.{FlowOffEvent, StartNewFlowEvent}
import scalasem.network.topology.{Host, GlobalDeviceManager, HostContainer}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser


class RivuaiApp(servers : HostContainer) extends ServerApp (servers) {

  private class RivuaiJob {
    var startTime = 0.0
    var inputSize = 0.0
    var inputPath = ""
  }

  private val fbTracePath = XmlParser.getString("scalasim.app.tracepath", "trace/trace.tsv")
  private val jobList = new ArrayBuffer[RivuaiJob]
  private val pathToServers = new HashMap[String, ArrayBuffer[String]]
  private val blockSize = XmlParser.getInt("scalasim.app.blocksize", 1024 * 1024 * 128)
  private var currentCursor = 0
  private val rivuaiAppAgen = new RivuaiAppAgent

  private def digestTrace() {
    for(line <- Source.fromFile(fbTracePath).getLines()) {
      val record = line.split("\t")
      val newJob = new RivuaiJob
      newJob.startTime = record(1).toDouble
      newJob.inputSize = record(3).toDouble
      newJob.inputPath = record(6)
      //evenly distribute the path in the servers
      if (!pathToServers.contains(record(6))) {
        //1. get the block number
        val blockNum = math.max(math.ceil(newJob.inputSize / blockSize).toInt, 1)
        val serverList = new ArrayBuffer[String]
        //2. evenly distribute the blocks
        for (bIdx <- 0 until blockNum) {
          serverList += servers(currentCursor).ip_addr(0)
          if (currentCursor == servers.size() - 1) {
            currentCursor = 0
          } else {
            currentCursor = currentCursor + 1
          }
        }
        //3. save to the pathToServers
        pathToServers += record(6) -> serverList
      }
    }
  }

  override def reset(){
    jobList.clear()
  }

  private def selectMapperServers() = {
    val mapperNum = Random.nextInt(servers.size())
    val selectedDestinationServers = new ArrayBuffer[Int]
    for (i <- 0 until mapperNum) {
      var idx = Random.nextInt(servers.size())
      while (selectedDestinationServers.contains(idx)) {
        idx = Random.nextInt(servers.size())
      }
      selectedDestinationServers += idx
    }
    selectedDestinationServers
  }

  def constructFlowInstallReq(newflow: Flow) = {
    val flowInstallReq = new FlowInstallRequest
    flowInstallReq.setSourceIP(IPAddressConvertor.DecimalStringToInt(newflow.srcIP).toInt)
    flowInstallReq.setDestinationIP(
      IPAddressConvertor.DecimalStringToInt(newflow.dstIP).toInt)
    flowInstallReq.setSourcePort(newflow.srcPort)
    flowInstallReq.setDestinationPort(newflow.dstPort)
    flowInstallReq.setSourcePort(newflow.srcPort)
    flowInstallReq.setDestinationPort(newflow.dstPort)
    flowInstallReq.setReqtype(1)
    flowInstallReq.setValue(1)
    flowInstallReq
  }

  override def run(){
    for (job <- jobList) {
      if (job.startTime > SimulationEngine.endTime) return
      //1. get the mapper number
      val blockServers = pathToServers.get(job.inputPath)
      //2. get the destination machines
      val destServers = selectMapperServers()
      //3. start the flows
      var currentIdx = 0
      for (serverIdx <- destServers) {
        if (servers(currentIdx).ip_addr(0) != blockServers.get(currentIdx)) {
          //generate new flow
          val newflow = Flow(blockServers.get(currentIdx),
            servers(serverIdx).ip_addr(0),
            GlobalDeviceManager.getNode(blockServers.get(currentIdx)).mac_addr(0),
            servers(serverIdx).mac_addr(0),
            sPort = Random.nextInt(65536).toShort,
            dPort = Random.nextInt(65536).toShort,
            appDataSize = 128 * 1024 * 1024)
          currentIdx = currentIdx + 1
          val newflowevent = new StartNewFlowEvent(
            newflow,
            GlobalDeviceManager.getNode(blockServers.get(currentIdx)).asInstanceOf[Host],
            job.startTime)
          SimulationEngine.addEvent(newflowevent)
          //start a off
          SimulationEngine.addEvent(new FlowOffEvent(newflow,
            job.startTime + Random.nextInt(OnOffApp.offLength)))
          //add request to the connlist
          val flowInstallReq = constructFlowInstallReq(newflow)
          rivuaiAppAgen.addFlowInstallRequest(flowInstallReq)
        }
      }
    }
  }

  digestTrace()
  rivuaiAppAgen.connectToController()
}
