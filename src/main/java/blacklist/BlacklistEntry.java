package blacklist;

import java.nio.charset.StandardCharsets;

/** One "server" block of server_blacklist.txt. Always blocks the whole IP (port 0). */
record BlacklistEntry(String name, long date, String ip) {

    /**
     * The game keeps at most 63 bytes of a name and cuts multi-byte characters in half when
     * it rewrites the file, so names are shortened here at a character boundary.
     */
    static final int MAX_NAME_BYTES = 63;

    BlacklistEntry {
        name = truncateUtf8(sanitize(name), MAX_NAME_BYTES);
    }

    static BlacklistEntry now(String name, String ip) {
        return new BlacklistEntry(name, System.currentTimeMillis() / 1000, ip);
    }

    /** Port 0 = all ports of the IP. */
    String addr() {
        return ip + ":0";
    }

    /**
     * KeyValues has no reliable escaping: drop quotes, braces and control characters,
     * plus U+FFFD left over from broken bytes in files the game wrote.
     */
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
            } else if (!Character.isISOControl(c) && c != '�') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static String truncateUtf8(String s, int maxBytes) {
        if (s.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > maxBytes) {
                break;
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        return sb.toString().trim();
    }
}
