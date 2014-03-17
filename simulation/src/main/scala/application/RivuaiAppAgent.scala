package scalasem.application

import java.util
import java.util.concurrent.Executors
import java.net.InetSocketAddress

import scala.collection.JavaConversions._

import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.bootstrap.ClientBootstrap
import nettychannel.{AppAgentMsgEncoder, AppAgentMsgDecoder}
import message.FlowInstallRequest

import scalasem.util.{XmlParser, Logging}

class RivuaiAppAgent extends Logging {

  class RivuaiAppMsgFactory extends ChannelPipelineFactory {
    def getPipeline: ChannelPipeline = {
      val p = Channels.pipeline()
      p.addLast("msg decoder", new AppAgentMsgDecoder)
      p.addLast("msg handler", new RivuaiAppMsgHandler)
      p.addLast("msg encoder", new AppAgentMsgEncoder)
      p
    }
  }

  class RivuaiAppMsgHandler extends SimpleChannelHandler with Runnable {
    var toControllerChannel: Channel = null
    override def run() {
      while (true) {
        if (connList.isEmpty) {
          try {
            Thread.sleep(10)
          }
          catch {
            case e: InterruptedException => {
              e.printStackTrace
            }
          }
        }
        else {
          connList.synchronized {
            toControllerChannel.write(connList)
            connList.clear()
          }
        }
      }
    }
    new Thread(this).start()
  }

  private val controllerIP = XmlParser.getString("scalasim.rivuai.controllerip", "127.0.0.1")
  private val port = XmlParser.getInt("scalasim.rivuai.controllerport", 6634)
  private val connList = new util.LinkedList[FlowInstallRequest]


  def addFlowInstallRequest(request: FlowInstallRequest) {
    connList += request
  }

  //build channel to the controller
  def connectToController() {
    val clientfactory = new NioClientSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool())
    val clientbootstrap = new ClientBootstrap(clientfactory)
    clientbootstrap.setOption("connectTimeoutMillis", 600000)
    clientbootstrap.setOption("keepAlive", true)
    clientbootstrap.setPipelineFactory(new RivuaiAppMsgFactory)
    while (true) {
      try {
        clientbootstrap.connect(new InetSocketAddress(controllerIP, port))
        return
      }
      catch {
        case e : Exception => {
          e.printStackTrace()
        }
      }
    }
  }
}
