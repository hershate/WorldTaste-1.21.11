package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.util.Colors;
import com.haiman233.worldtaste.util.Stacks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerHead;
import io.github.thebusybiscuit.slimefun4.libraries.dough.skins.PlayerSkin;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

/**
 * 共享读取器：把一个物品段（{@code material_type/material/name/lore/glow/amount}）解析为 {@link ItemStack}，
 * 以及把配方段（槽位 1..N）解析为 {@code ItemStack[]}。对应 RSC 的 {@code CommonUtils.readItem/readRecipe}。
 */
public final class Read {

    private Read() {}

    private static final Pattern HEX64 = Pattern.compile("^[0-9A-Fa-f]{64}$");

    /** 读取物品段。{@code countable=true} 时应用 amount。 */
    public static ItemStack item(ConfigurationSection s, boolean countable) {
        if (s == null) return null;
        String material = s.getString("material", "");
        if (material.isEmpty()) return null;

        String type = s.getString("material_type", "mc");
        String lower = material.toLowerCase(java.util.Locale.ROOT);
        // 自动识别覆盖（与 RSC CommonUtils.readItem 一致）
        if (lower.startsWith("ey") || lower.startsWith("ew")) type = "skull";
        else if (lower.startsWith("http")) type = "skull_url";
        else if (HEX64.matcher(material).matches()) type = "skull_hash";

        ItemStack stack = resolve(type, material);
        if (stack == null) return null;

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = s.getString("name");
            if (name != null && !name.isEmpty()) meta.setDisplayName(Colors.c(name));
            List<String> lore = s.getStringList("lore");
            if (lore != null && !lore.isEmpty()) meta.setLore(Colors.c(lore));
            String color = s.getString("color");
            if (color != null && !color.isEmpty()) applyColor(meta, color);
            stack.setItemMeta(meta);
        }

        if (s.getBoolean("glow", false)) Stacks.glow(stack);
        if (countable) {
            int amt = s.getInt("amount", 1);
            if (amt > 0) stack.setAmount(Math.min(amt, stack.getMaxStackSize()));
        }
        return stack;
    }

    private static ItemStack resolve(String type, String material) {
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "none":
                return new ItemStack(Material.AIR);
            case "skull_hash":
                return PlayerHead.getItemStack(PlayerSkin.fromHashCode(material));
            case "skull":
            case "skull_base64":
                return PlayerHead.getItemStack(PlayerSkin.fromBase64(material));
            case "skull_url":
                return PlayerHead.getItemStack(PlayerSkin.fromURL(material));
            case "slimefun": {
                String id = material.toUpperCase(java.util.Locale.ROOT);
                ItemStack pre = WT.preload.get(id);
                if (pre != null) return pre.clone();
                SlimefunItem sf = SlimefunItem.getById(id);
                if (sf != null) return sf.getItem();
                WT.log("未找到粘液物品: " + id + "，回退为 STONE");
                return new ItemStack(Material.STONE);
            }
            default: {
                Material m = matchMaterial(material);
                if (m == null) {
                    WT.log("未知材质: " + material + "，回退为 STONE");
                    return new ItemStack(Material.STONE);
                }
                return new ItemStack(m);
            }
        }
    }

    private static Material matchMaterial(String name) {
        if (name == null) return null;
        Material m = Material.matchMaterial(name);
        if (m != null) return m;
        // 别名（1.21 改名）
        if (name.equalsIgnoreCase("GRASS")) return Material.matchMaterial("SHORT_GRASS");
        if (name.equalsIgnoreCase("SCUTE")) return Material.matchMaterial("TURTLE_SCUTE");
        return Material.matchMaterial(name.replace('-', '_'));
    }

    /** 读取配方段，槽位键 "1".."size"，产出长度为 size 的数组（空槽为 null）。 */
    public static ItemStack[] recipe(ConfigurationSection recipeSec, int size) {
        ItemStack[] out = new ItemStack[size];
        if (recipeSec == null) return out;
        for (int i = 0; i < size; i++) {
            ConfigurationSection slot = recipeSec.getConfigurationSection(String.valueOf(i + 1));
            if (slot != null) out[i] = item(slot, true);
        }
        return out;
    }

    /** 应用 "R,G,B" 颜色到皮革护甲/药水等可染色 meta。 */
    private static void applyColor(ItemMeta meta, String color) {
        String[] rgb = color.split(",");
        if (rgb.length != 3) return;
        try {
            int r = Integer.parseInt(rgb[0].trim());
            int g = Integer.parseInt(rgb[1].trim());
            int b = Integer.parseInt(rgb[2].trim());
            Color c = Color.fromRGB(r, g, b);
            if (meta instanceof LeatherArmorMeta) ((LeatherArmorMeta) meta).setColor(c);
            else if (meta instanceof PotionMeta) ((PotionMeta) meta).setColor(c);
        } catch (NumberFormatException ignored) {
            WT.log("颜色格式错误: " + color);
        }
    }
}
