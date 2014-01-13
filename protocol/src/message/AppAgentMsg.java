package message;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.util.U16;

public class AppAgentMsg {
  protected static int MINIMUM_LENGTH = 3;

  protected AppMsgType type = null;
  protected short length = 0;

  public void readFrom(ChannelBuffer buffer) {
    this.type = AppMsgType.valueOf(buffer.readByte());
    this.length = buffer.readShort();
  }

  public void writeTo(ChannelBuffer data) {
    data.writeByte(type.getTypeValue());
    data.writeByte(length);
  }

  public int getLengthU() {
    return U16.f(length);
  }

  public AppMsgType getType() {return type;}
}
