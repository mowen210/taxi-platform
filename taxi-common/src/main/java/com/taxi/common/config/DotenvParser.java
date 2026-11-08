package com.taxi.common.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析 KEY=VALUE 形式的统一配置文件（.env）。
 * <p>支持注释、export 前缀、单/双引号、双引号内转义；不支持多行值。</p>
 */
public final class DotenvParser {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private DotenvParser() {
    }

    public static Map<String, String> parse(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        boolean first = true;
        for (String raw : lines) {
            String line = raw;
            if (first) {
                first = false;
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                continue;
            }
            result.put(key, parseValue(line.substring(eq + 1).trim()));
        }
        return result;
    }

    private static String parseValue(String raw) {
        if (raw.length() >= 2) {
            char quote = raw.charAt(0);
            if ((quote == '"' || quote == '\'') && raw.charAt(raw.length() - 1) == quote) {
                String value = raw.substring(1, raw.length() - 1);
                return quote == '"' ? unescape(value) : value;
            }
        }
        int comment = raw.indexOf(" #");
        if (comment >= 0) {
            raw = raw.substring(0, comment).trim();
        }
        return raw;
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                sb.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }
}
