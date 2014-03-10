package scalasem.util

import scala.xml.XML
import scala.collection.mutable.HashMap

object XmlParser {

  private val properties = new HashMap[String, String]()

  def set(key : String, value : String) {
    properties += key -> value
  }

  def loadConf(confPath:String) = {
    val xmldata = XML.loadFile(confPath)
    for (ele <- xmldata \\ "property" ) {
      properties += ((ele \\ "name").text -> (ele \\ "value").text)
    }
  }

  def reset() = properties.clear()

  def getBoolean(key:String, defaultValue : Boolean) : Boolean = {
    properties.getOrElse(key, defaultValue.toString).toBoolean
  }


  def getString(key:String, defaultValue:String) : String = {
    properties.getOrElse(key, defaultValue)
  }

  //TODO: can I do better?
  def getInt(key:String, defaultValue:Int) : Int = {
    properties.getOrElse(key, defaultValue.toString).toInt
  }

  def getDouble(key:String, defaultValue:Double) : Double = {
    properties.getOrElse(key, defaultValue.toString).toDouble
  }
}
