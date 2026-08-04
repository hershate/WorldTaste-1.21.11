package com.haiman233.worldtaste.machines;

import com.haiman233.worldtaste.WT;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.inventory.InvUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * 电力配方机器（recipe_machines.yml）。继承 {@link AContainer} 复用能量/进度/GUI 基建，
 * 自定义 {@link #findNextRecipe} 与 {@link #tick} 以支持 {@link WTRecipe} 的概率产出与 noConsume。
 */
public class WTRecipeMachine extends AContainer implements RecipeDisplayItem {

    private final int[] inputSlots;
    private final int[] outputSlots;
    private final List<WTRecipe> recipes;
    private final MenuDef menu;
    private final boolean hideAll;
    private final ItemStack progressBar;
    /** 进行中配方（按方块），完成时取此处的 WTRecipe 做概率滚动。 */
    private final Map<org.bukkit.Location, WTRecipe> active = new ConcurrentHashMap<>();

    public WTRecipeMachine(ItemGroup group, SlimefunItemStack item, RecipeType rt, ItemStack[] recipe,
                           int[] inputSlots, int[] outputSlots, List<WTRecipe> recipes,
                           int capacity, int consumption, int speed, MenuDef menu, boolean hideAll) {
        super(group, item, rt, recipe);
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        this.recipes = recipes;
        this.menu = menu;
        this.hideAll = hideAll;
        this.progressBar = (menu != null && menu.progressItem != null)
                ? menu.progressItem : new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        setProcessingSpeed(Math.max(1, speed));
        setCapacity(Math.max(1, capacity));
        setEnergyConsumption(Math.max(1, Math.min(consumption, Math.max(1, capacity))));
    }

    @Override
    public int[] getInputSlots() { return inputSlots; }

    @Override
    public int[] getOutputSlots() { return outputSlots; }

    @Override
    public String getMachineIdentifier() { return getId(); }

    @Override
    public ItemStack getProgressBar() { return progressBar; }

    @Override
    protected void registerDefaultRecipes() { /* 配方由本类自行管理 */ }

    private int progressSlot() {
        return (menu != null && menu.progressSlot >= 0) ? menu.progressSlot : 22;
    }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        Set<Integer> placed = new HashSet<>();
        // 装饰（来自菜单）
        if (menu != null) {
            for (Map.Entry<Integer, ItemStack> e : menu.items.entrySet()) {
                preset.addItem(e.getKey(), e.getValue(), ChestMenuUtils.getEmptyClickHandler());
                placed.add(e.getKey());
            }
        }
        int pslot = progressSlot();
        // 功能槽
        Set<Integer> functional = new HashSet<>();
        for (int s : inputSlots) functional.add(s);
        for (int s : outputSlots) functional.add(s);
        if (pslot >= 0) functional.add(pslot);
        // 背景填充剩余槽位
        int size = (menu != null && menu.size > 0) ? menu.size : 27;
        for (int i = 0; i < size; i++) {
            if (!functional.contains(i) && !placed.contains(i)) {
                preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }
        }
        // 进度占位
        if (pslot >= 0 && !placed.contains(pslot)) {
            preset.addItem(pslot, progressBar, ChestMenuUtils.getEmptyClickHandler());
        }
        // 输出槽允许取出
        for (int i : outputSlots) {
            preset.addMenuClickHandler(i, ChestMenuUtils.getDefaultOutputHandler());
        }
    }

    @Override
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b.getLocation());
                if (inv != null) {
                    inv.dropItems(b.getLocation(), inputSlots);
                    inv.dropItems(b.getLocation(), outputSlots);
                }
                active.remove(b.getLocation());
                getMachineProcessor().endOperation(b);
            }
        };
    }

    @Override
    protected void tick(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b.getLocation());
        if (inv == null) return;
        int pslot = progressSlot();
        CraftingOperation op = getMachineProcessor().getOperation(b);
        if (op == null) {
            MachineRecipe next = findNextRecipe(inv);
            if (next instanceof WTRecipe) {
                op = new CraftingOperation(next);
                getMachineProcessor().startOperation(b, op);
                active.put(b.getLocation(), (WTRecipe) next);
                getMachineProcessor().updateProgressBar(inv, pslot, op);
            }
            return;
        }
        if (!takeCharge(b.getLocation())) return;
        if (!op.isFinished()) {
            getMachineProcessor().updateProgressBar(inv, pslot, op);
            op.addProgress(1);
            return;
        }
        WTRecipe r = active.remove(b.getLocation());
        if (r != null) {
            r.pushOutputs(inv, outputSlots);
        }
        inv.replaceExistingItem(pslot, progressBar);
        getMachineProcessor().endOperation(b);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        int[] slots = inputSlots;
        int slotCount = slots.length;
        ItemStack[] slotItems = new ItemStack[slotCount];
        for (int s = 0; s < slotCount; s++) {
            ItemStack it = inv.getItemInSlot(slots[s]);
            slotItems[s] = (it == null) ? null : ItemStackWrapper.wrap(it);
        }
        for (WTRecipe recipe : recipes) {
            ItemStack[] inputs = recipe.getInput();
            int n = inputs.length;
            int[] chosen = new int[n];
            int matched = 0;
            for (int i = 0; i < n; i++) {
                chosen[i] = -1;
                ItemStack need = inputs[i];
                if (need == null) { matched++; continue; }
                for (int s = 0; s < slotCount; s++) {
                    ItemStack in = slotItems[s];
                    if (in != null && in.getAmount() >= need.getAmount() && SlimefunUtils.isItemSimilar(in, need, true)) {
                        chosen[i] = s;
                        matched++;
                        break;
                    }
                }
            }
            if (matched != n) continue;
            int distinct = 0;
            for (int i = 0; i < n; i++) {
                boolean dup = false;
                for (int j = 0; j < i; j++) if (chosen[i] == chosen[j]) { dup = true; break; }
                if (!dup) distinct++;
            }
            if (distinct != n) continue;
            if (!InvUtils.fitAll(inv.toInventory(), recipe.getOutput(), outputSlots)) return null;
            for (int i = 0; i < n; i++) {
                if (!recipe.isNoConsume(i) && chosen[i] >= 0) {
                    inv.consumeItem(slots[chosen[i]], inputs[i].getAmount());
                }
            }
            return recipe;
        }
        return null;
    }

    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> out = new ArrayList<>();
        if (hideAll) return out;
        for (WTRecipe r : recipes) {
            ItemStack[] in = r.getInput();
            ItemStack[] res = r.getOutput();
            out.add(in.length > 0 && in[0] != null ? in[0] : new ItemStack(Material.BARRIER));
            out.add(res.length > 0 ? res[0] : new ItemStack(Material.BARRIER));
        }
        return out;
    }
}
