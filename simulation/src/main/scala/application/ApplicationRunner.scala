package scalasem.application

import scala.collection.mutable

import scalasem.network.topology.HostContainer
import scalasem.util.XmlParser

object ApplicationRunner {

  private var resourcePool : HostContainer = null
  private val apps = new mutable.HashMap[String, ServerApp]

  def setResource(r : HostContainer) {
    resourcePool = r
  }

  def installApplication() {
    if (resourcePool == null)
      throw new Exception("you haven't assign the resource to the application")
    val appNames:String = XmlParser.getString("scalasim.application.names", "PermuMatrixApp")
    val namesStr = appNames.split(',')
    for (name <- namesStr) apps += name -> ServerApp(name, resourcePool)
  }

  def run() { for (app <- apps.valuesIterator) app.run() }

  def reset() {
    for (app <- apps.values) app.reset
  }

  def apply(appName: String): ServerApp = apps(appName)

}
