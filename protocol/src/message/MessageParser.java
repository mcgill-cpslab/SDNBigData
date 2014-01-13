package message;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.factory.MessageParseException;

import java.util.ArrayList;
import java.util.List;

public class MessageParser {

  public List<AppAgentMsg> parseMessage(ChannelBuffer data) throws MessageParseException {

    List<AppAgentMsg> msglist = new ArrayList<AppAgentMsg>();
    AppAgentMsg msg = null;

    while (data.readableBytes() >= OFMessage.MINIMUM_LENGTH) {
      data.markReaderIndex();
      msg = this.parseMessageOne(data);
      if (msg == null) {
        data.resetReaderIndex();
        break;
      }
      else {
        msglist.add(msg);
      }
    }

    if (msglist.size() == 0) {
      return null;
    }
    return msglist;
  }

  private AppAgentMsg parseMessageOne(ChannelBuffer data) throws MessageParseException {
    try {
      AppAgentMsg demux = new AppAgentMsg();
      AppAgentMsg aam = null;

      if (data.readableBytes() < 1)
        return aam;

      data.markReaderIndex();
      demux.readFrom(data);
      data.resetReaderIndex();

      if (demux.getLengthU() > data.readableBytes())
        return aam;

      aam = getMessage(demux.getType());
      if (aam == null)
        return null;

      aam.readFrom(data);
      if (OFMessage.class.equals(aam.getClass())) {
        // advance the position for un-implemented messages
        data.readerIndex(data.readerIndex()+(aam.getLengthU() -
                OFMessage.MINIMUM_LENGTH));
      }

      return aam;
    } catch (Exception e) {
            /* Write the offending data along with the error message */
      data.resetReaderIndex();
      String msg =
              "Message Parse Error for packet:" +  dumpBuffer(data) +
                      "\nException: " + e.toString();
      data.resetReaderIndex();

      throw new MessageParseException(msg, e);
    }
  }

  private AppAgentMsg getMessage(AppMsgType type) {
    return type.newInstance();
  }

  public static String dumpBuffer(ChannelBuffer data) {
    // NOTE: Reads all the bytes in buffer from current read offset.
    // Set/Reset ReaderIndex if you want to read from a different location
    int len = data.readableBytes();
    StringBuffer sb = new StringBuffer();
    for (int i=0 ; i<len; i++) {
      if (i%32 == 0) sb.append("\n");
      if (i%4 == 0) sb.append(" ");
      sb.append(String.format("%02x", data.getUnsignedByte(i)));
    }
    return sb.toString();
  }
}
