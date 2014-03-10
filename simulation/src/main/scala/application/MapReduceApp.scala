package scalasem.application

import scala.util.Random

import scalasem.network.topology.HostContainer
import scalasem.network.events.{FlowOffEvent, StartNewFlowEvent}
import scalasem.network.traffic.Flow
import scalasem.simengine.SimulationEngine
import scalasem.util.XmlParser


class MapReduceApp (servers : HostContainer) extends ServerApp (servers) {


  private val jobnum = XmlParser.getInt("scalasim.app.mapreduce.jobnum", 10)
  private val arrivalinterval = XmlParser.getDouble("scalasim.app.mapreduce.interval", 10)
  private val maxmappernum = XmlParser.getInt("scalasim.app.mapreduce.maxmapnum", 20)
  private val maxreducernum = XmlParser.getInt("scalasim.app.mapreduce.maxreducenum", 20)
  private val flowsize = XmlParser.getInt("scalasim.app.mapreduce.flowsize", 100)


  def generateJob(startTime : Double) {
    var selectedMapperIndices = List[Int]()
    var selectedReducerIndices = List[Int]()

    def selectMapperServers() = {
      val mapperNum = Random.nextInt(maxmappernum)
      for (i <- 0 until mapperNum) {
        var idx = Random.nextInt(servers.size())
        while (selectedMapperIndices.contains(idx) ||
          selectedReducerIndices.contains(idx)) {
          idx = Random.nextInt(maxmappernum)
        }
        selectedMapperIndices = idx :: selectedMapperIndices
      }
    }

    def selectReducerServers() {
      val reducerNum = Random.nextInt(maxreducernum)
      for (i <- 0 until reducerNum) {
        var idx = Random.nextInt(servers.size())
        while (selectedMapperIndices.contains(idx) ||
          selectedReducerIndices.contains(idx)) {
          idx = Random.nextInt(maxreducernum)
        }
        selectedReducerIndices = idx :: selectedReducerIndices
      }
    }

    selectMapperServers()
    selectReducerServers()

    for (i <- 0 until selectedMapperIndices.length;
         j <- 0 until selectedReducerIndices.length) {
      val flow = Flow(servers(selectedMapperIndices(i)).ip_addr(0),
        servers(selectedReducerIndices(j)).ip_addr(0),
        servers(selectedMapperIndices(i)).mac_addr(0),
        servers(selectedReducerIndices(j)).mac_addr(0),
        appDataSize = flowsize)
      val newflowevent = new StartNewFlowEvent(
        flow,
        servers(selectedMapperIndices(i)),
        startTime)
      SimulationEngine.addEvent(newflowevent)
      //start a off
      SimulationEngine.addEvent(new FlowOffEvent(flow,
        startTime + Random.nextInt(OnOffApp.offLength)))
    }
  }

  def run() {
    for (i <- 0 until jobnum)
      generateJob(SimulationEngine.currentTime + i * arrivalinterval)
  }

  def reset() {

  }

}
