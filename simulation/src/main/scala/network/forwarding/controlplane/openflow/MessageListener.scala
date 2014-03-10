package scalasem.network.forwarding.controlplane.openflow

import org.openflow.protocol.OFMessage

/**
 * the base type for the handler of messages
 */
trait MessageListener {

  def handleMessage (msg : OFMessage)
}
