package blacklist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** IPv4 helpers. */
final class Ip {

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private Ip() {
    }

    /** Returns the IPv4 address as unsigned long, or -1 if invalid. */
    static long toLong(String ip) {
        Matcher m = IPV4.matcher(ip.trim());
        if (!m.matches()) {
            return -1;
        }
        long value = 0;
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(m.group(i));
            if (octet > 255) {
                return -1;
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    static boolean isValid(String ip) {
        return toLong(ip) >= 0;
    }

    /** Normalizes "1.2.3.4" or "1.2.3.4:27015" to the bare IP, or null if invalid. */
    static String hostOf(String addr) {
        String a = addr.trim();
        int colon = a.indexOf(':');
        String host = colon >= 0 ? a.substring(0, colon) : a;
        long v = toLong(host);
        if (v < 0) {
            return null;
        }
        return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    /** Returns the port of "ip:port", 0 if absent, -1 if invalid. */
    static int portOf(String addr) {
        String a = addr.trim();
        int colon = a.indexOf(':');
        if (colon < 0) {
            return 0;
        }
        try {
            int port = Integer.parseInt(a.substring(colon + 1));
            return port >= 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
