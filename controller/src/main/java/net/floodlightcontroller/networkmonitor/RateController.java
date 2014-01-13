package net.floodlightcontroller.networkmonitor;


import message.AppAgentMsg;
import message.FlowInstallRequest;
import message.FlowInstallResponse;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.internal.Entity;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFSwitchRateLimitingState;
import org.openflow.protocol.OFType;
import org.openflow.util.U8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

import nettychannel.*;

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
  private HashMap<IOFSwitch, SwitchRateLimiterStatus> switchRateLimitMap = null;
  private HashMap<String, IOFSwitch> switchMap = null;
  private HashMap<IOFSwitch, ArrayList<FlowInstallRequest>> flowtoInstallList = null;
  private IOFSwitch coreSwitch = null;
  private AppAgentMsgFactory aamFactory = null;
  private AppAgentChannelHandler channelHandler = null;
  private int flowtablelimit = 200;


  private class SwitchRateLimiterStatus {
    private int tablesize;
    private boolean alldeadlinebound;
    private int ratelimitinglowerbound;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    logger = LoggerFactory.getLogger(RateController.class);
    switchRateLimitMap = new HashMap<IOFSwitch, SwitchRateLimiterStatus>();
    switchMap = new HashMap<String, IOFSwitch>();//switch IP segment => IOFSwitch
    channelHandler = new AppAgentChannelHandler(this);
    flowtoInstallList = new HashMap<IOFSwitch, ArrayList<FlowInstallRequest>>();
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
      case SWITCH_RATE_LIMITING_STATE:
        OFSwitchRateLimitingState slsmsg = (OFSwitchRateLimitingState) msg;
        SwitchRateLimiterStatus obj = new SwitchRateLimiterStatus();
        obj.ratelimitinglowerbound = slsmsg.getDeadlinelowbound();
        obj.tablesize = slsmsg.getTablesize();
        if (slsmsg.getAlldeadlinebound() == 1)
          obj.alldeadlinebound = true;
        else
          obj.alldeadlinebound = false;
        switchRateLimitMap.put(sw, obj);
        //install the flows
        for (FlowInstallRequest request: flowtoInstallList.get(sw)) {
          //if (request)
          //send FLOW_MOD
        }
        break;
      case GET_CONFIG_REPLY:
        flowtoInstallList.put(sw, new ArrayList<FlowInstallRequest>());
        break;
    }
    return null;
  }

  private Entity getSourceEntityFromInstallRequest(FlowInstallRequest request) {

    return null;
  }

  private String ipToString(int ip) {
    return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24))) + "."
            + Integer.toString((ip & 0x00ff0000) >> 16) + "."
            + Integer.toString((ip & 0x0000ff00) >> 8) + "."
            + Integer.toString(ip & 0x000000ff);
  }

  private boolean canInstall(IOFSwitch switchobj, FlowInstallRequest request) {
    SwitchRateLimiterStatus srlobj = switchRateLimitMap.get(switchobj);
    if (srlobj.tablesize + flowtoInstallList.get(switchobj).size()
            < flowtablelimit) {
      if (request.getDeadline() != -1) {
        //the request flow is deadline-bound
        if (!srlobj.alldeadlinebound) return true;
        //all flows currently in the switch are deadline-bound
        return request.getDeadline() < srlobj.ratelimitinglowerbound;
      }
    } else {
      //the request flow is not deadline-bound
      if (srlobj.alldeadlinebound) return false;
      return (request.getFlowsize() / request.getDeadline()) < srlobj.ratelimitinglowerbound;
    }
    return true;
  }

  //TODO
  private boolean installFlowToSwitch(FlowInstallRequest request) {
    //get the ingress and egress switch
    String sourceIP = ipToString(request.getSourceIP());
    String dstIP = ipToString(request.getDestinationIP());
    String sourceRange = sourceIP.substring(0, sourceIP.lastIndexOf(".") + 1) + ".0";
    String dstRange = dstIP.substring(0, dstIP.lastIndexOf(".") + 1) + ".0";
    IOFSwitch ingressSwitch = switchMap.get(sourceRange);
    IOFSwitch egressSwitch = switchMap.get(dstRange);
    boolean canInstall =  canInstall(ingressSwitch, request) && canInstall(egressSwitch, request) &&
            canInstall(coreSwitch, request);
    if (canInstall) {
      flowtoInstallList.get(ingressSwitch).add(request);
      flowtoInstallList.get(egressSwitch).add(request);
      flowtoInstallList.get(coreSwitch).add(request);
    }
    return canInstall;
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
  //  l.add(IDeviceService.class);
    return l;
  }

  @Override
  public void startUp(FloodlightModuleContext context) {
    floodlightProvider.addOFMessageListener(OFType.GET_CONFIG_REPLY, this);
    floodlightProvider.addOFMessageListener(OFType.SWITCH_RATE_LIMITING_STATE, this);
  }
}
