package nettychannel;

import message.AppAgentMsg;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import java.util.List;

public class AppAgentMsgEncoder extends OneToOneEncoder {
  @Override
  protected Object encode(ChannelHandlerContext channelHandlerContext,
                          Channel channel, Object msg) throws Exception {
    if (!(msg instanceof List))
      return msg;

    @SuppressWarnings("unchecked")
    List<AppAgentMsg> msglist = (List<AppAgentMsg>)msg;
    int size = 0;
    for (AppAgentMsg aam :  msglist) {
      size += aam.getLengthU();
    }

    ChannelBuffer buf = ChannelBuffers.buffer(size);;
    for (AppAgentMsg aam :  msglist) {
      aam.writeTo(buf);
    }
    return buf;
  }
}
