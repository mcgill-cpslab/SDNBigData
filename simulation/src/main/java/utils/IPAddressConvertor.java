package utils;

public class IPAddressConvertor {
    public static long DecimalStringToInt (String ipstr) {
        try {
            String [] ipsegs = ipstr.split("\\.");
            long ret = 0;
            if (ipsegs.length != 4)
                throw new Exception("ip str is not in correct format, length = " + ipsegs.length +
                 ", ipstr = " + ipstr);
            for (int i = 0; i < 4; i++) {
                ret += Math.pow(256.0, 3 - i) * Integer.parseInt(ipsegs[i]);
            }
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static String IntToDecimalString (int ipInt) {
        Integer [] bytes = new Integer[4];
        bytes[0] = ipInt & 0xFF;
        bytes[1] = (ipInt >> 8) & 0xFF;
        bytes[2] = (ipInt >> 16) & 0xFF;
        bytes[3] = (ipInt >> 24) & 0xFF;
        return String.format("%d.%d.%d.%d", bytes[3], bytes[2], bytes[1], bytes[0]);
    }
}
