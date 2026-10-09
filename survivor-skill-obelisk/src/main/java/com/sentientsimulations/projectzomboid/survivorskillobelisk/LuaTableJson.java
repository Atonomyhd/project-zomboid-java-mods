package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;

/**
 * Minimal JSON codec for Kahlua tables, used to store Lifestyles' nested {@code invData} as a
 * single TEXT column. PZ ships no JSON library, so this is hand-rolled; it only has to round-trip
 * what Lua itself can hold: strings, numbers, booleans and nested tables.
 *
 * <p>A table whose keys are exactly {@code 1..n} encodes as a JSON array; every other table encodes
 * as an object with string keys, sorted so output is deterministic. On decode, object keys that
 * look like numbers become Lua numbers again, which keeps sparse numeric keys stable across a round
 * trip (Lifestyles never uses digit-only string keys). Values no JSON type can carry (Java objects,
 * NaN, infinities) are dropped, as is {@code nil}.
 */
final class LuaTableJson {

    private static final int MAX_DEPTH = 32;
    private static final Pattern NUMERIC_KEY = Pattern.compile("-?\\d+(\\.\\d+)?");

    private LuaTableJson() {}

    static String encode(KahluaTable table) {
        StringBuilder sb = new StringBuilder();
        writeTable(table, sb, 0);
        return sb.toString();
    }

    /** Returns null for malformed input or a document whose root is not an object or array. */
    static KahluaTable decode(String json, Supplier<KahluaTable> newTable) {
        if (json == null) {
            return null;
        }
        try {
            Parser parser = new Parser(json, newTable);
            Object root = parser.readValue(0);
            parser.skipWhitespace();
            if (!parser.atEnd() || !(root instanceof KahluaTable table)) {
                return null;
            }
            return table;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean writeValue(Object value, StringBuilder sb, int depth) {
        if (value instanceof String s) {
            writeString(s, sb);
            return true;
        }
        if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
            return true;
        }
        if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                return false;
            }
            writeNumber(d, sb);
            return true;
        }
        if (value instanceof KahluaTable t) {
            if (depth >= MAX_DEPTH) {
                return false;
            }
            writeTable(t, sb, depth);
            return true;
        }
        return false;
    }

    private static void writeNumber(double d, StringBuilder sb) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(Double.toString(d));
        }
    }

    private static void writeTable(KahluaTable table, StringBuilder sb, int depth) {
        int arrayLength = arrayLength(table);
        if (arrayLength > 0) {
            sb.append('[');
            for (int i = 1; i <= arrayLength; i++) {
                if (i > 1) {
                    sb.append(',');
                }
                if (!writeValue(table.rawget(i), sb, depth + 1)) {
                    sb.append("null");
                }
            }
            sb.append(']');
            return;
        }
        List<Object[]> entries = new ArrayList<>();
        KahluaTableIterator it = table.iterator();
        while (it.advance()) {
            String key = keyString(it.getKey());
            if (key != null) {
                entries.add(new Object[] {key, it.getValue()});
            }
        }
        entries.sort(Comparator.comparing(e -> (String) e[0]));
        sb.append('{');
        boolean first = true;
        for (Object[] entry : entries) {
            int mark = sb.length();
            if (!first) {
                sb.append(',');
            }
            writeString((String) entry[0], sb);
            sb.append(':');
            if (!writeValue(entry[1], sb, depth + 1)) {
                sb.setLength(mark);
                continue;
            }
            first = false;
        }
        sb.append('}');
    }

    /** Length n when the keys are exactly the integers 1..n, otherwise 0. */
    private static int arrayLength(KahluaTable table) {
        int count = 0;
        double max = 0;
        KahluaTableIterator it = table.iterator();
        while (it.advance()) {
            if (!(it.getKey() instanceof Double d)) {
                return 0;
            }
            double v = d;
            if (v < 1 || v != Math.rint(v)) {
                return 0;
            }
            max = Math.max(max, v);
            count++;
        }
        return count > 0 && max == count ? count : 0;
    }

    private static String keyString(Object key) {
        if (key instanceof String s) {
            return s;
        }
        if (key instanceof Double d && !d.isNaN() && !d.isInfinite()) {
            StringBuilder sb = new StringBuilder();
            writeNumber(d, sb);
            return sb.toString();
        }
        return null;
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {

        private final String s;
        private final Supplier<KahluaTable> newTable;
        private int pos;

        Parser(String s, Supplier<KahluaTable> newTable) {
            this.s = s;
            this.newTable = newTable;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= s.length()) {
                throw new IllegalStateException("unexpected end of JSON");
            }
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(String literal) {
            if (!s.startsWith(literal, pos)) {
                throw new IllegalStateException("expected " + literal + " at " + pos);
            }
            pos += literal.length();
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalStateException("JSON nested too deep");
            }
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private KahluaTable readObject(int depth) {
            expect("{");
            KahluaTable table = newTable.get();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return table;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalStateException("expected object key at " + pos);
                }
                String key = readString();
                skipWhitespace();
                expect(":");
                Object value = readValue(depth + 1);
                if (value != null) {
                    table.rawset(luaKey(key), value);
                }
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return table;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or } at " + (pos - 1));
                }
            }
        }

        private KahluaTable readArray(int depth) {
            expect("[");
            KahluaTable table = newTable.get();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return table;
            }
            int index = 1;
            while (true) {
                Object value = readValue(depth + 1);
                if (value != null) {
                    table.rawset(Double.valueOf(index), value);
                }
                index++;
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return table;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or ] at " + (pos - 1));
                }
            }
        }

        private String readString() {
            expect("\"");
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
                char escaped = next();
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw new IllegalStateException("truncated \\u escape");
                        }
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalStateException("bad escape \\" + escaped);
                }
            }
        }

        private Double readNumber() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9')
                        || c == '-'
                        || c == '+'
                        || c == '.'
                        || c == 'e'
                        || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalStateException("unexpected character at " + pos);
            }
            return Double.valueOf(s.substring(start, pos));
        }

        private static Object luaKey(String key) {
            if (NUMERIC_KEY.matcher(key).matches()) {
                return Double.valueOf(key);
            }
            return key;
        }
    }
}
