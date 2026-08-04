package com.haiman233.worldtaste.machines;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.List;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 工作台（workbenches.yml，如百味万用炉）：复用 {@link WTRecipeMachine} 的配方/能量/GUI，
 * 但不自动 tick 合成；改为玩家点击 click 槽位时即时合成（消耗能量、匹配并消耗输入、产出）。
 */
public class WTWorkbench extends WTRecipeMachine {

    private final int clickSlot;

    public WTWorkbench(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe,
                       int[] input, int[] output, List<WTRecipe> recipes,
                       int capacity, int consumption, int speed, MenuDef menu, boolean hideAll, int clickSlot) {
        super(group, item, rt, recipe, input, output, recipes, capacity, consumption, speed, menu, hideAll);
        this.clickSlot = clickSlot;
        // 用自定义 preset 覆盖 AContainer 默认 preset，在 newInstance 挂接点击合成
        new BlockMenuPreset(getId(), getItemName()) {
            @Override
            public void init() {
                WTWorkbench.this.constructMenu(this);
            }

            @Override
            public void newInstance(BlockMenu menu, Block b) {
                menu.addMenuClickHandler(clickSlot, (player, slot, clicked, action) -> {
                    WTWorkbench.this.craft(menu);
                    return false;
                });
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return flow == ItemTransportFlow.INSERT ? getInputSlots() : getOutputSlots();
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass") || WTWorkbench.this.canUse(p, false);
            }
        };
    }

    @Override
    protected void tick(Block b) {
        // 工作台不自动合成，仅在点击 click 槽时合成
    }

    private void craft(BlockMenu menu) {
        if (!takeCharge(menu.getLocation())) return;
        MachineRecipe next = findNextRecipe(menu);
        if (!(next instanceof WTRecipe)) return;
        WTRecipe r = (WTRecipe) next;
        r.pushOutputs(menu, getOutputSlots());
    }
}
