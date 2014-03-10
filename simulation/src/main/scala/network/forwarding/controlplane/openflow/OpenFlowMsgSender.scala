package scalasem.network.forwarding.controlplane.openflow

import scala.collection.immutable.ListSet

import org.openflow.protocol.OFMessage
import org.jboss.netty.channel.Channel


class OpenFlowMsgSender () {

  //used to batch IO
  private var ioBatchBuffer = new ListSet[OFMessage]

  //used to store those messages pended for the unconnected messages
  private var msgPendingBuffer = new ListSet[OFMessage]

  def pushInToBuffer (msg : OFMessage) {
    ioBatchBuffer +=  msg
  }

  def sendMessageToController(channel : Channel, message : OFMessage) {
    msgPendingBuffer += message
    if (channel != null && channel.isConnected) {
      channel.write(msgPendingBuffer)
      msgPendingBuffer = new ListSet[OFMessage]
    }
  }

  def flushBuffer(channel : Channel) {
    try {
      if (channel != null && channel.isConnected) {
        channel.write(ioBatchBuffer)
        ioBatchBuffer = new ListSet[OFMessage]
      }
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
}
