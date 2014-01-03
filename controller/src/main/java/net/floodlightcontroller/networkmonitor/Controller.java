package net.floodlightcontroller.networkmonitor;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class Controller implements IOFMessageListener, IFloodlightModule {

  protected IFloodlightProviderService floodlightProvider;
  protected static Logger logger;

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    logger = LoggerFactory.getLogger(Controller.class);
  }

  @Override
  public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
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
    Collection<Class<? extends IFloodlightService>> l =
            new ArrayList<Class<? extends IFloodlightService>>();
    l.add(IFloodlightProviderService.class);
    return l;
  }

  @Override
  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    return null;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    return null;
  }

  @Override
  public void startUp(FloodlightModuleContext context) {

  }
}
