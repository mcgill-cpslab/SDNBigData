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
import net.floodlightcontroller.packet.Ethernet;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.openflow.protocol.*;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

import nettychannel.*;
import utils.Utils;

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
    public void channelConnected (ChannelHandlerContext ctx, ChannelStateEvent e) {
      String host = ((InetSocketAddress) e.getChannel().getRemoteAddress()).getAddress().getHostName();
      int port = ((InetSocketAddress) e.getChannel().getRemoteAddress()).getPort();
      logger.debug(host + ":" + port + " connected");
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
  private AppAgentMsgFactory aamFactory = null;
  private AppAgentChannelHandler channelHandler = null;
  private int flowtablelimit = 200;
  private HashMap<Integer, Integer> mactable = null;

  private class SwitchRateLimiterStatus {
    private int tablesize;
  }

  private void initAppPool() {
    //bind to a new port to communicate with the application agents
    logger.info("starting Rivuai Rate Controller");
    ChannelFactory factory = new NioServerSocketChannelFactory(
            Executors.newSingleThreadExecutor(),
            Executors.newCachedThreadPool());
    ServerBootstrap bootstrap = new ServerBootstrap(factory);
    aamFactory = new AppAgentMsgFactory(this);
    bootstrap.setPipelineFactory(aamFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    bootstrap.bind(new InetSocketAddress(6634));
    logger.info("start Rivuai Rate Controller successfully");
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    mactable = new HashMap<Integer, Integer>();
    logger = LoggerFactory.getLogger(RateController.class);
    switchRateLimitMap = new HashMap<IOFSwitch, SwitchRateLimiterStatus>();
    switchMap = new HashMap<String, IOFSwitch>();//switch IP segment => IOFSwitch
    channelHandler = new AppAgentChannelHandler(this);
    flowtoInstallList = new HashMap<IOFSwitch, ArrayList<FlowInstallRequest>>();
    initAppPool();
  }

  //TODO
  private OFFlowMod1 getFlowModFromInstallReq(FlowInstallRequest req, boolean exactIP) {
    OFFlowMod1 ret = new OFFlowMod1();
    OFMatch match = new OFMatch();
    match.setNetworkSource(req.getSourceIP());
    match.setNetworkDestination(req.getDestinationIP());
    match.setTransportSource(req.getSourcePort());
    match.setTransportDestination(req.getDestinationPort());
    match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_NW_SRC_ALL & ~OFMatch.OFPFW_NW_DST_ALL &
            ~OFMatch.OFPFW_TP_DST & ~OFMatch.OFPFW_TP_SRC);
    ret.setMatch(match);
    OFActionOutput actionOutput = new OFActionOutput();
    if (exactIP) {
      actionOutput.setPort(mactable.get(match.getNetworkDestination()).shortValue());
    } else {
      String dstIP = Utils.IntIPToString(mactable.get(match.getNetworkDestination()));
      String dstIPrange = getIPRange(dstIP);
      actionOutput.setPort((short) Utils.StringIPToInteger(dstIPrange));
    }
    ArrayList<OFAction> list = new ArrayList<OFAction>();
    list.add(actionOutput);
    ret.setActions(list);
    ret.setBufferId(-1);
    ret.setJobid(req.getIdx());
    ret.setReqvalue(req.getValue());
    ret.setReqtype(req.getReqtype());
    return ret;
  }

  private String getIPRange(String ip) {
    return ip.substring(0, ip.lastIndexOf(".")) + ".0";
  }

  private boolean sameIPRange(String ip1, String ip2) {
    String range1 = ip1.substring(0, ip1.lastIndexOf(".")) + ".0";
    String range2 = ip2.substring(0, ip2.lastIndexOf(".")) + ".0";
    return range1.equals(range2);
  }

  @Override
  /**
   * the main service
   */
  public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    switch (msg.getType()) {
      case PACKET_IN:
        OFPacketIn pi = (OFPacketIn) msg;
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        if (match.getDataLayerType() != 0x0806 && match.getDataLayerType() != 0x0800) {
          break;
        } else {
          //must be in the same pod with the source ip
          if (sameIPRange(Utils.IntIPToString(match.getNetworkSource()),
                  ((InetSocketAddress)
                          sw.getChannel().getRemoteAddress()).getAddress().getHostAddress()) &&
                  !mactable.containsKey(match.getNetworkSource())) {
            mactable.put(match.getNetworkSource(), (int) pi.getInPort());
          }
        }
        break;
      case SWITCH_RATE_LIMITING_STATE:
        OFSwitchRateLimitingState slsmsg = (OFSwitchRateLimitingState) msg;
        SwitchRateLimiterStatus obj = new SwitchRateLimiterStatus();
        obj.tablesize = slsmsg.getTablesize();
        switchRateLimitMap.put(sw, obj);
        //install the flows
        for (FlowInstallRequest request: flowtoInstallList.get(sw)) {
          //if (request)
          try {
            OFFlowMod1 flowmodmsg = getFlowModFromInstallReq(request, true);
            sw.write(flowmodmsg, cntx);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        break;
      case GET_CONFIG_REPLY:
        if (!flowtoInstallList.containsKey(sw)) {
          String swip = ((InetSocketAddress)
                  sw.getChannel().getRemoteAddress()).getAddress().getHostAddress();
          flowtoInstallList.put(sw, new ArrayList<FlowInstallRequest>());
          switchMap.put(getIPRange(swip), sw);
        }
        break;
    }
    return null;
  }

  private boolean canInstall(IOFSwitch switchobj, FlowInstallRequest request) {
    SwitchRateLimiterStatus srlobj = switchRateLimitMap.get(switchobj);
    if (srlobj.tablesize + flowtoInstallList.get(switchobj).size()
            < flowtablelimit) {
      return true;
    }
    return false;
  }

  //TODO
  private boolean installFlowToSwitch(FlowInstallRequest request) {
    //get the ingress and egress switch
    String sourceIP = Utils.IntIPToString(request.getSourceIP());
    //String dstIP = Utils.IntIPToString(request.getDestinationIP());
    String sourceRange = sourceIP.substring(0, sourceIP.lastIndexOf(".") + 1) + ".0";
    //String dstRange = dstIP.substring(0, dstIP.lastIndexOf(".") + 1) + ".0";
    IOFSwitch ingressSwitch = switchMap.get(sourceRange);
    //IOFSwitch egressSwitch = switchMap.get(dstRange);
    boolean canInstall =  canInstall(ingressSwitch, request); //&&canInstall(egressSwitch, request);
    if (canInstall) {
      synchronized (flowtoInstallList) {
        flowtoInstallList.get(ingressSwitch).add(request);
        //flowtoInstallList.get(egressSwitch).add(request);
      }
    }
    return canInstall;
  }

  //TODO
  private void processAppMessage(AppAgentMsg msg) {
    switch (msg.getType()) {
      case FLOW_INSTALL_REQUEST:
        FlowInstallRequest req = (FlowInstallRequest) msg;
        boolean installSuccess = installFlowToSwitch(req);
        byte installSuccessByte = 0;
        if (installSuccess) installSuccessByte = 1;
        FlowInstallResponse response = new FlowInstallResponse();
        response.setInstalledSuccessfully(installSuccessByte);
        response.setIdx(req.getIdx());
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
    floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
  }
}
