package scalasem.network.forwarding.controlplane.openflow

import org.jboss.netty.channel.{Channels, ChannelPipeline, ChannelPipelineFactory}

class OpenFlowMsgPipelineFactory (connector : OpenFlowControlPlane) extends ChannelPipelineFactory {
  def getPipeline: ChannelPipeline = {
    val p = Channels.pipeline()
    p.addLast("msg decoder", new OpenFlowMsgDecoder)
    p.addLast("msg handler", new OpenFlowMessageDispatcher(connector))
    p.addLast("msg encoder", new OpenFlowMsgEncoder)
    p
  }
}
