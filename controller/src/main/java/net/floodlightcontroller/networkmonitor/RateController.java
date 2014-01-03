package net.floodlightcontroller.networkmonitor;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.networkmonitor.message.AppAgentMsg;
import net.floodlightcontroller.networkmonitor.message.MessageParser;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

public class RateController implements IOFMessageListener, IFloodlightModule {

  private class AppAgentMsgDecoder extends FrameDecoder {
    private MessageParser parser = new MessageParser();
    @Override
    protected List<AppAgentMsg> decode(ChannelHandlerContext channelHandlerContext,
                            Channel channel, ChannelBuffer channelBuffer) throws Exception {
      if (!channel.isConnected()) return null;
      return parser.parseMessage(channelBuffer);
    }
  }

  private class AppAgentMsgEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext channelHandlerContext,
                            Channel channel, Object msg) throws Exception {
      if (!(  msg instanceof List))
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

  private class AppAgentChannelHandler extends SimpleChannelHandler {

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent msgEvent) {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent expEvent) {

    }
  }

  protected IFloodlightProviderService floodlightProvider;
  protected static Logger logger;
  private HashMap<IOFSwitch, SwitchRateLimiterStatus> switchHashMap = null;

  private class SwitchRateLimiterStatus {
    private int tablesize;
    private int ratelimitinglowerbound;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    logger = LoggerFactory.getLogger(RateController.class);
    switchHashMap = new HashMap<IOFSwitch, SwitchRateLimiterStatus>();
    //bind to a new port to communicate with the application agents
    ChannelFactory factory = new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
    ServerBootstrap bootstrap = new ServerBootstrap(factory);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(new AppAgentChannelHandler());
      }
    });
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    bootstrap.bind(new InetSocketAddress(6634));
  }

  private void processFlowInstallRequest() {

  }

  private void processSwitchRateLimitingRate() {

  }

  @Override
  /**
   * the main service
   */
  public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
      case ECHO_REPLY:
        if (!switchHashMap.containsKey(sw))
          switchHashMap.put(sw, new SwitchRateLimiterStatus());
        break;
      case FLOW_INSTALL_REQUEST:
        processFlowInstallRequest();
        break;
      case SWITCH_RATE_LIMITING_STATE:
        processSwitchRateLimitingRate();
        break;
    }
    return null;
  }

  @Override
  public String getName() {
    return "RateController";
  }

  @Override
  public boolean isCallbackOrderingPrereq(OFType type, String name) {
    return false;
  }

  @Override
  public boolean isCallbackOrderingPostreq(OFType type, String name) {
    return false;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    return null;
  }

  @Override
  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    return null;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    Collection<Class<? extends IFloodlightService>> l =
            new ArrayList<Class<? extends IFloodlightService>>();
    l.add(IFloodlightProviderService.class);
    return l;
  }

  @Override
  public void startUp(FloodlightModuleContext context) {
    floodlightProvider.addOFMessageListener(OFType.ECHO_REPLY, this);
    floodlightProvider.addOFMessageListener(OFType.FLOW_INSTALL_REQUEST, this);
  }
}
