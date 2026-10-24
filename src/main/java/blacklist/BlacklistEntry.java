package blacklist;

/** One "server" block of server_blacklist.txt. Always blocks the whole IP (port 0). */
record BlacklistEntry(String name, long date, String ip) {

    BlacklistEntry {
        name = sanitize(name);
    }

    static BlacklistEntry now(String name, String ip) {
        return new BlacklistEntry(name, System.currentTimeMillis() / 1000, ip);
    }

    /** Port 0 = all ports of the IP. */
    String addr() {
        return ip + ":0";
    }

    /** KeyValues has no reliable escaping: drop quotes, braces and control characters. */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '"') {
                sb.append('\'');
            } else if (c == '{' || c == '}') {
                sb.append(c == '{' ? '(' : ')');
            } else if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
