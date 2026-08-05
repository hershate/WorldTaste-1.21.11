package com.haiman233.worldtaste.behavior;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.load.Yaml;
import com.haiman233.worldtaste.util.Stacks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 百味钓竿钓鱼（对齐原 diaoyu.js + wt_fishing.js）：主手持指定钓竿、副手持已知鱼饵时，
 * 取消原掉落、消耗 1 鱼饵、按权重随机产出 1 个物品并拉向玩家。
 */
public final class FishingListener implements Listener {

    public static final FishingListener INSTANCE = new FishingListener();

    private static String rodId = "WT_BAIWEIDIAOGAN";
    private static final Map<String, List<Drop>> baits = new HashMap<>();

    private FishingListener() {}

    public static void load() {
        YamlConfiguration y = Yaml.loadResource(WT.plugin, "data/fishing.yml");
        rodId = y.getString("rod", rodId);
        ConfigurationSection bs = y.getConfigurationSection("baits");
        baits.clear();
        if (bs != null) {
            for (String bait : bs.getKeys(false)) {
                List<Drop> drops = new ArrayList<>();
                for (Map<?, ?> m : bs.getMapList(bait)) {
                    Object id = m.get("id");
                    Object w = m.get("weight");
                    if (id != null && w instanceof Number) {
                        drops.add(new Drop((String) id, ((Number) w).intValue()));
                    }
                }
                baits.put(bait, drops);
            }
        }
        int total = baits.values().stream().mapToInt(List::size).sum();
        WT.plugin.getLogger().info("行为数据: fishing rod=" + rodId + " baits=" + baits.size() + " drops=" + total);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || e.getCaught() == null) return;
        Player p = e.getPlayer();
        SlimefunItem rod = SlimefunItem.getByItem(p.getInventory().getItemInMainHand());
        if (rod == null || !rod.getId().equals(rodId)) return;
        SlimefunItem bait = SlimefunItem.getByItem(p.getInventory().getItemInOffHand());
        if (bait == null) return;
        List<Drop> drops = baits.get(bait.getId());
        if (drops == null) return;

        // 先选并解析掉落物；无法解析（如未装对应附属）时不取消事件、不扣饵、保留原渔获
        Drop d = select(drops);
        if (d == null) return;
        ItemStack stack = resolve(d.id);
        if (stack == null) return;

        e.setCancelled(true);
        // 鱼饵耗尽到 0 必须清空副手槽位：否则残留 0 数量幽灵物品仍被识别为该鱼饵，
        // 玩家可无消耗无限钓获（复制漏洞）。
        Stacks.consumeOneInOffHand(p.getInventory());
        e.getCaught().remove();

        stack.setAmount(1);
        Item ent = e.getHook().getWorld().dropItem(e.getHook().getLocation(), stack);
        ent.setPickupDelay(2);
        Vector dir = p.getLocation().add(0, 1, 0).toVector()
                .subtract(ent.getLocation().toVector()).normalize().multiply(1.7);
        ent.setVelocity(dir);
        p.sendMessage("§b恭喜你钓到了 " + displayName(stack) + " §b*1");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private static Drop select(List<Drop> drops) {
        int total = 0;
        for (Drop d : drops) total += d.weight;
        if (total <= 0) return null;
        double r = Math.random() * total;
        for (Drop d : drops) {
            r -= d.weight;
            if (r <= 0) return d;
        }
        return drops.get(drops.size() - 1);
    }

    private static ItemStack resolve(String id) {
        SlimefunItem sf = SlimefunItem.getById(id);
        if (sf != null) return sf.getItem().clone();
        Material m = Material.matchMaterial(id);
        return m == null ? null : new ItemStack(m);
    }

    private static String displayName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        return stack.getType().name().toLowerCase().replace('_', ' ');
    }

    private static final class Drop {
        final String id;
        final int weight;
        Drop(String id, int weight) { this.id = id; this.weight = weight; }
    }
}
