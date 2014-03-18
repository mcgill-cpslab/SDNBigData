package scalasem.application

import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.Random

import message.FlowInstallRequest
import utils.IPAddressConvertor

import scalasem.network.events.{FlowOffEvent, StartNewFlowEvent}
import scalasem.network.topology.{Host, GlobalDeviceManager, HostContainer}
import scalasem.network.traffic.{GlobalFlowStore, Flow}
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser


class RivuaiApp(servers : HostContainer) extends ServerApp (servers) {

  private class RivuaiJob {
    var jobId = -1
    var startTime = 0.0
    var inputSize = 0.0
    var inputPath = ""

    def mapTaskNum = math.ceil(inputSize / (blockSize * 1024 * 1024)).toInt
  }

  private val fbTracePath = XmlParser.getString("scalasim.app.tracepath", "trace/trace.tsv")
  private val jobList = new ArrayBuffer[RivuaiJob]
  private val pathToServers = new HashMap[String, ArrayBuffer[String]]
  private val blockSize = XmlParser.getInt("scalasim.app.blocksize", 128)//in MB
  private var currentCursor = 0
  private val rivuaiAppAgen = new RivuaiAppAgent

  private def digestTrace() {
    logTrace("Loading Workload Trace")
    for(line <- Source.fromFile(fbTracePath).getLines()) {
      val record = line.split("\t")
      val newJob = new RivuaiJob
      newJob.jobId = record(0).substring(record(0).indexOf('b') + 1, record(0).length).toInt
      newJob.startTime = record(1).toDouble
      newJob.inputSize = record(3).toDouble
      newJob.inputPath = record(6)
      jobList += newJob
      logTrace("created new job " + newJob.jobId)
      //evenly distribute the path in the servers
      if (!pathToServers.contains(record(6))) {
        //1. get the block number
        val blockNum = math.max(math.ceil(newJob.inputSize / (blockSize * 1024 * 1024)).toInt, 1)
        val serverList = new ArrayBuffer[String]
        //2. evenly distribute the blocks
        for (bIdx <- 0 until blockNum) {
          serverList += servers(currentCursor).ip_addr(0)
          logTrace("assigned the block of %s to server %s".format(record(6),
            servers(currentCursor).ip_addr(0)))
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

  private def selectMapperServers(mapperNum: Int) = {
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

  def constructFlowInstallReq(newflow: Flow, reqType: Int, reqValue: Int) = {
    val flowInstallReq = new FlowInstallRequest
    flowInstallReq.setSourceIP(IPAddressConvertor.DecimalStringToInt(newflow.srcIP).toInt)
    flowInstallReq.setDestinationIP(
      IPAddressConvertor.DecimalStringToInt(newflow.dstIP).toInt)
    flowInstallReq.setSourcePort(newflow.srcPort)
    flowInstallReq.setDestinationPort(newflow.dstPort)
    flowInstallReq.setSourcePort(newflow.srcPort)
    flowInstallReq.setDestinationPort(newflow.dstPort)
    flowInstallReq.setReqtype(reqType)
    flowInstallReq.setValue(reqValue)
    flowInstallReq
  }

  override def run(){
    for (job <- jobList) {
      if (job.startTime > SimulationEngine.endTime) return
      //1. get the mapper number
      val blockServers = pathToServers.get(job.inputPath)
      //2. get the destination machines
      val destServers = selectMapperServers(job.mapTaskNum)
      //3. start the flows
      var currentIdx = 0
      for (serverIdx <- destServers) {
        if (servers(serverIdx).ip_addr(0) != blockServers.get(currentIdx)) {
          //generate new flow
          val newflow = Flow(
            blockServers.get(currentIdx),
            servers(serverIdx).ip_addr(0),
            GlobalDeviceManager.getNode(blockServers.get(currentIdx)).mac_addr(0),
            servers(serverIdx).mac_addr(0),
            sPort = Random.nextInt(65536).toShort,
            dPort = Random.nextInt(65536).toShort,
            appDataSize = blockSize)
          logDebug("created new flow" + newflow.toString())
          val newflowevent = new StartNewFlowEvent(
            newflow,
            GlobalDeviceManager.getNode(blockServers.get(currentIdx)).asInstanceOf[Host],
            job.startTime)
          SimulationEngine.addEvent(newflowevent)
          //start a off
          SimulationEngine.addEvent(new FlowOffEvent(newflow,
            job.startTime + Random.nextInt(OnOffApp.offLength)))
          //add request to the connlist
          val flowInstallReq = constructFlowInstallReq(newflow, 1, job.jobId % 2 + 1)
          rivuaiAppAgen.addFlowInstallRequest(flowInstallReq)
          currentIdx = currentIdx + 1
        }
      }
    }
  }

  digestTrace()
  rivuaiAppAgen.connectToController()
}
