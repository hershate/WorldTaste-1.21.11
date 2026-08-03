package com.haiman233.worldtaste.machines;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** 菜单定义（来自 menus.yml）：标题/尺寸/装饰槽位/进度槽。纯数据，由机器在 constructMenu 中使用。 */
public final class MenuDef {

    public final String id;
    public final String title;
    public int size = -1;
    public int progressSlot = -1;
    public ItemStack progressItem;
    public final Map<Integer, ItemStack> items = new HashMap<>();

    public MenuDef(String id, String title) {
        this.id = id;
        this.title = title;
    }
}
