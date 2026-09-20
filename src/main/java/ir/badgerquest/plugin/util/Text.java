package ir.badgerquest.plugin.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    private Text() {}

    public static String color(String in) {
        if (in == null) return "";
        return ChatColor.translateAlternateColorCodes('&', in);
    }

    public static List<String> color(List<String> in) {
        if (in == null) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) out.add(color(s));
        return out;
    }

    public static String apply(String in, Map<String, String> placeholders) {
        if (in == null) return "";
        String out = in;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    public static List<String> apply(List<String> in, Map<String, String> placeholders) {
        if (in == null) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) out.add(apply(s, placeholders));
        return out;
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
