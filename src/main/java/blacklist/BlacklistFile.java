package blacklist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes Valve KeyValues server_blacklist.txt files. */
final class BlacklistFile {

    private static final String NL = "\r\n";

    private BlacklistFile() {
    }

    static void write(Path file, Collection<BlacklistEntry> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\"serverblacklist\"").append(NL).append('{').append(NL);
        for (BlacklistEntry e : entries) {
            sb.append("\t\"server\"").append(NL);
            sb.append("\t{").append(NL);
            sb.append("\t\t\"name\"\t\t\t\"").append(e.name()).append('"').append(NL);
            sb.append("\t\t\"date\"\t\t\t\"").append(e.date()).append('"').append(NL);
            sb.append("\t\t\"addr\"\t\t\t\"").append(e.addr()).append('"').append(NL);
            sb.append("\t}").append(NL);
        }
        sb.append('}').append(NL);
        // String.getBytes(UTF_8) never writes a BOM.
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Parses all "server" blocks; entries with invalid addr are skipped. */
    static List<BlacklistEntry> read(Path file) throws IOException {
        // Lenient decoding: the game cuts names after 63 bytes, often in the middle of a UTF-8 character.
        // new String(...) replaces such broken sequences instead of failing like Files.readString.
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<String> tokens = tokenize(text);
        List<BlacklistEntry> result = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            if (tokens.get(i).equalsIgnoreCase("server") && i + 1 < tokens.size() && tokens.get(i + 1).equals("{")) {
                Map<String, String> kv = new HashMap<>();
                i += 2;
                while (i < tokens.size() && !tokens.get(i).equals("}")) {
                    if (i + 1 < tokens.size()) {
                        kv.put(tokens.get(i).toLowerCase(), tokens.get(i + 1));
                    }
                    i += 2;
                }
                toEntry(kv, result);
            }
            i++;
        }
        return result;
    }

    private static void toEntry(Map<String, String> kv, List<BlacklistEntry> out) {
        String addr = kv.get("addr");
        if (addr == null) {
            return;
        }
        // Any port in the source file is dropped: entries always block the whole IP.
        String ip = Ip.hostOf(addr);
        if (ip == null) {
            return;
        }
        long date;
        try {
            date = Long.parseLong(kv.getOrDefault("date", "0").trim());
        } catch (NumberFormatException e) {
            date = System.currentTimeMillis() / 1000;
        }
        out.add(new BlacklistEntry(kv.getOrDefault("name", ip), date, ip));
    }

    /** Splits KeyValues text into quoted/unquoted strings and braces; strips // comments. */
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '{' || c == '}') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '"') {
                int end = text.indexOf('"', i + 1);
                if (end < 0) {
                    end = n;
                }
                tokens.add(text.substring(i + 1, end));
                i = end + 1;
            } else {
                int start = i;
                while (i < n && !Character.isWhitespace(text.charAt(i)) && "{}\"".indexOf(text.charAt(i)) < 0) {
                    i++;
                }
                tokens.add(text.substring(start, i));
            }
        }
        return tokens;
    }
}
