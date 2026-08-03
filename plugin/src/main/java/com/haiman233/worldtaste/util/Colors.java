package com.haiman233.worldtaste.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

/**
 * 颜色工具：兼容 WorldTaste 中三种十六进制写法 {@code &#RRGGBB}、{@code {#RRGGBB}}，
 * 以及标准 {@code &a/&l/...} 颜色/格式码。
 */
public final class Colors {

    private Colors() {}

    private static final Pattern HEX_BRACE = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");
    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String c(String s) {
        if (s == null) return null;
        String t = replaceHex(HEX_BRACE, s);
        t = replaceHex(HEX_AMP, t);
        return ChatColor.translateAlternateColorCodes('&', t);
    }

    public static List<String> c(List<String> list) {
        if (list == null) return null;
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(c(s));
        return out;
    }

    private static String replaceHex(Pattern p, String input) {
        Matcher m = p.matcher(input);
        if (!m.find()) return input;
        StringBuffer sb = new StringBuffer();
        m.reset();
        while (m.find()) m.appendReplacement(sb, toBukkitHex(m.group(1)));
        m.appendTail(sb);
        return sb.toString();
    }

    private static String toBukkitHex(String hex) {
        StringBuilder b = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) b.append('§').append(Character.toLowerCase(hex.charAt(i)));
        return b.toString();
    }
}
