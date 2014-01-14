package utils;

import org.openflow.util.U8;

public class Utils {

  public static int StringIPToInteger(String ip) {
    String[] ip_str = ip.split("\\.");
    int ret = 0;
    ret += Integer.valueOf(ip_str[0]) << 24;
    ret += Integer.valueOf(ip_str[1]) << 16;
    ret += Integer.valueOf(ip_str[2]) << 8;
    ret += Integer.valueOf(ip_str[3]);
    return ret;
  }

  public static String IntIPToString(int ip) {
    return Integer.toString(U8.f((byte) ((ip & 0xff000000) >> 24))) + "."
            + Integer.toString((ip & 0x00ff0000) >> 16) + "."
            + Integer.toString((ip & 0x0000ff00) >> 8) + "."
            + Integer.toString(ip & 0x000000ff);
  }

}
