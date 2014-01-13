package nettychannel;

import message.AppAgentMsg;
import message.MessageParser;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.util.List;

public class AppAgentMsgDecoder extends FrameDecoder {

  private MessageParser parser = new MessageParser();

  @Override
  protected List<AppAgentMsg> decode(ChannelHandlerContext channelHandlerContext,
                                     Channel channel, ChannelBuffer channelBuffer) throws Exception {
    if (!channel.isConnected()) return null;
    return parser.parseMessage(channelBuffer);
  }
}
