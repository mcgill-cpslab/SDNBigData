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
import net.floodlightcontroller.networkmonitor.message.FlowInstallRequest;
import net.floodlightcontroller.networkmonitor.message.FlowInstallResponse;
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

  private class AppAgentMsgFactory implements ChannelPipelineFactory {

    private RateController rateController;

    AppAgentMsgFactory(RateController controller) {
      rateController = controller;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      ChannelPipeline p = Channels.pipeline();
      p.addLast("msg decoder", new AppAgentMsgDecoder());
      p.addLast("msg dispatcher", new AppAgentChannelHandler(rateController));
      p.addLast("msg encoder", new AppAgentMsgEncoder());
      return p;
    }
  }

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

    private RateController rateController;
    private ArrayList<AppAgentMsg> outBuffer;
    private int outBufferSize;

    AppAgentChannelHandler (RateController controller) {
      rateController = controller;
      outBuffer = new ArrayList<AppAgentMsg>();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent msgEvent) {
      if (!(msgEvent.getMessage() instanceof List)) return;
      List<AppAgentMsg> msglist = (List<AppAgentMsg>) msgEvent.getMessage();
      for (AppAgentMsg msg : msglist) {
        rateController.processAppMessage(msg);
      }
      //flush the outbuffer
      msgEvent.getChannel().write(outBuffer);
      outBuffer.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent expEvent) {
      expEvent.getCause().printStackTrace();
    }
  }

  protected IFloodlightProviderService floodlightProvider;
  protected static Logger logger;
  private HashMap<IOFSwitch, SwitchRateLimiterStatus> switchHashMap = null;
  private AppAgentMsgFactory aamFactory = null;
  private AppAgentChannelHandler channelHandler = null;

  private class SwitchRateLimiterStatus {
    private int tablesize;
    private int ratelimitinglowerbound;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    logger = LoggerFactory.getLogger(RateController.class);
    switchHashMap = new HashMap<IOFSwitch, SwitchRateLimiterStatus>();
    channelHandler = new AppAgentChannelHandler(this);
    //bind to a new port to communicate with the application agents
    ChannelFactory factory = new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
    ServerBootstrap bootstrap = new ServerBootstrap(factory);
    aamFactory = new AppAgentMsgFactory(this);
    bootstrap.setPipelineFactory(aamFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    bootstrap.bind(new InetSocketAddress(6634));
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
    }
    return null;
  }

  private boolean installFlowToSwitch(FlowInstallRequest request) {

    return false;
  }

  //TODO
  private void processAppMessage(AppAgentMsg msg) {
    switch (msg.getType()) {
      case FLOW_INSTALL_REQUEST:
        boolean installSuccess = installFlowToSwitch((FlowInstallRequest) msg);
        byte installSuccessByte = 0;
        if (installSuccess) installSuccessByte = 1;
        FlowInstallResponse response = new FlowInstallResponse();
        response.setInstalledSuccessfully(installSuccessByte);
        channelHandler.outBuffer.add(response);
        break;
      case FLOW_PROBING:
        break;
    }
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
