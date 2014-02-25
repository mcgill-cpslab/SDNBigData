package message;


import org.openflow.protocol.Instantiable;

public enum AppMsgType {
  FLOW_INSTALL_REQUEST (0, FlowInstallRequest.class, new Instantiable<AppAgentMsg>() {
    @Override
    public AppAgentMsg instantiate() {
      return new FlowInstallRequest();
    }}),
  FLOW_INSTALL_RESPONSE (1, FlowInstallResponse.class, new Instantiable<AppAgentMsg>() {
    @Override
    public AppAgentMsg instantiate() {
      return new FlowInstallResponse();
    }
  }),
  FLOW_PROBING (2, FlowProbing.class, new Instantiable<AppAgentMsg>() {
    @Override
    public AppAgentMsg instantiate() {
      return new FlowProbing();
    }
  });

  static AppMsgType[] mapping = null;
  protected Instantiable<AppAgentMsg> instantiator = null;
  private byte type = -1;

  AppMsgType(int i, Class<? extends AppAgentMsg> flowProbingClass,
             Instantiable<AppAgentMsg> instantiable) {
    type = (byte) i;
    instantiator = instantiable;
    AppMsgType.addMapping((byte)i, this);
  }

  public static void addMapping(byte i, AppMsgType type) {
    if (mapping == null)
      mapping = new AppMsgType[255];
    mapping[i] = type;
  }

  public AppAgentMsg newInstance() {
    return instantiator.instantiate();
  }

  public static AppMsgType valueOf(byte i) {
    return mapping[i];
  }

  public byte getTypeValue() {return this.type;}
}