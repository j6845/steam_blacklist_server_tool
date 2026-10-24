package blacklist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser: objects -> Map, arrays -> List, numbers -> Double, plus String/Boolean/null. */
final class Json {

    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.s.length()) {
            throw p.error("Unerwartete Zeichen am Ende");
        }
        return v;
    }

    private Object value() {
        if (pos >= s.length()) {
            throw error("Unerwartetes Ende");
        }
        char c = s.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            map.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("',' oder '}' erwartet");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(value());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("',' oder ']' erwartet");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (pos + 4 > s.length()) {
                        throw error("Ungültiges \\u-Escape");
                    }
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: sb.append(e);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("Wert erwartet");
        }
        return Double.parseDouble(s.substring(start, pos));
    }

    private void literal(String word) {
        if (!s.startsWith(word, pos)) {
            throw error("'" + word + "' erwartet");
        }
        pos += word.length();
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= s.length()) {
            throw error("Unerwartetes Ende");
        }
        return s.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("'" + c + "' erwartet");
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON-Fehler bei Position " + pos + ": " + msg);
    }
}
