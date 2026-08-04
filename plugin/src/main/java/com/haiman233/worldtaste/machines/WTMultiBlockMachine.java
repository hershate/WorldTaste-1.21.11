package com.haiman233.worldtaste.machines;

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块机器（mb_machines.yml）。继承 Slimefun 原生 {@link MultiBlockMachine}，
 * 右键结构时检查发射器内物品、消耗并产出。逻辑对齐 RSC CustomMultiBlockMachine。
 */
public class WTMultiBlockMachine extends MultiBlockMachine {

    private final int workIndex;
    private final BlockFace dispenserFace;
    @Nullable
    private final SoundEffect craftSound;

    public WTMultiBlockMachine(ItemGroup group, SlimefunItemStack item, ItemStack[] structure,
                               Map<ItemStack[], ItemStack> recipes, int work, @Nullable SoundEffect sound) {
        super(group, item, structure, BlockFace.SELF);
        this.workIndex = work - 1;
        this.craftSound = sound;
        this.dispenserFace = dispenserFaceGet();
        for (Map.Entry<ItemStack[], ItemStack> e : recipes.entrySet()) {
            addRecipe(e.getKey(), e.getValue());
        }
    }

    @Override
    public void onInteract(Player p, Block block) {
        Material material = getRecipe()[workIndex].getType();
        if (!block.getType().equals(material)) return;
        Block disBlock = block.getRelative(dispenserFace);
        BlockState bs = PaperLib.getBlockState(disBlock, false).getState();
        if (!(bs instanceof Dispenser dispenser)) return;

        Inventory inv = dispenser.getInventory();
        ItemStack[] contents = inv.getContents();
        java.util.List<ItemStack[]> inputs = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.getRecipeInputList(this);

        for (ItemStack[] input : inputs) {
            if (!isCraftable(contents, input)) continue;
            ItemStack output = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.getRecipeOutputList(this, input).clone();
            MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !SlimefunUtils.canPlayerUseItem(p, output, true)) return;
            Inventory fakeInv = createVirtualInventory(inv);
            Inventory outputInv = findOutputInventory(output, disBlock, inv, fakeInv);
            if (outputInv == null) {
                Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
                return;
            }
            for (int j = 0; j < input.length; j++) {
                ItemStack item = contents[j];
                if (item != null && item.getType() != Material.AIR) {
                    ItemUtils.consumeItem(item, input[j].getAmount(), true);
                }
            }
            if (craftSound != null) craftSound.playAt(block);
            outputInv.addItem(output);
            return;
        }

        if (inv.isEmpty()) {
            Slimefun.getLocalization().sendMessage(p, "machines.inventory-empty", true);
        } else {
            Slimefun.getLocalization().sendMessage(p, "machines.pattern-not-found", true);
        }
    }

    private boolean isCraftable(ItemStack[] contents, ItemStack[] recipe) {
        for (int j = 0; j < contents.length; j++) {
            if (!SlimefunUtils.isItemSimilar(contents[j], recipe[j], true, true, false)) {
                if (!SlimefunUtils.isItemSimilar(contents[j], recipe[j], false, true, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    protected Inventory createVirtualInventory(Inventory inv) {
        Inventory fakeInv = Bukkit.createInventory(null, 9, Component.text("Fake Inventory"));
        ItemStack[] contents = inv.getContents();
        for (int j = 0; j < contents.length; j++) {
            ItemStack stack = contents[j];
            if (stack != null) {
                stack = stack.clone();
                ItemUtils.consumeItem(stack, true);
            }
            fakeInv.setItem(j, stack);
        }
        return fakeInv;
    }

    private BlockFace dispenserFaceGet() {
        int center = workIndex;
        ItemStack[] is = getRecipe();
        if (center - 3 >= 0) {
            ItemStack o1 = is[center - 3];
            if (o1 != null && o1.getType() == Material.DISPENSER) return BlockFace.UP;
        }
        if (center - 1 >= 0) {
            ItemStack o2 = is[center - 1];
            if (o2 != null && o2.getType() == Material.DISPENSER) return BlockFace.EAST;
        }
        if (center + 1 >= 9) return BlockFace.SELF;
        ItemStack o3 = is[center + 1];
        if (o3 != null && o3.getType() == Material.DISPENSER) return BlockFace.WEST;
        if (center + 3 < 9) {
            ItemStack o4 = is[center + 3];
            if (o4 != null && o4.getType() == Material.DISPENSER) return BlockFace.DOWN;
        }
        return BlockFace.SELF;
    }
}
