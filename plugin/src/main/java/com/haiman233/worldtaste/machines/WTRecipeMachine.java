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
        // AContainer 在 super() 中已用 this::constructMenu 建过 preset（此时字段尚未赋值），
        // 这里字段就绪后重建 preset（覆盖前一个），并补设进度条。
        createPreset(this, getInventoryTitle(), this::constructMenu);
        getMachineProcessor().setProgressBar(progressBar);
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

    /** 额外的可交互槽位（不会被背景填充阻挡），子类（如模板机器）可覆盖以加入模板槽等。 */
    protected java.util.Set<Integer> extraFunctionalSlots() {
        return java.util.Collections.emptySet();
    }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        // super() 阶段会提前调用一次（字段为 null），此时跳过；由构造器末尾重建 preset 时再真正构建。
        if (inputSlots == null || outputSlots == null) return;
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
        java.util.Set<Integer> extra = extraFunctionalSlots();
        // 尺寸必须覆盖所有功能槽(input/output/progress/extra)与装饰槽：
        // BlockMenuPreset 按“已放置物品”自动定尺寸，而 output 槽只挂 click handler、不放置物品。
        // 若某功能槽超出自动尺寸(如机器无对应菜单、或菜单装饰未覆盖该槽)，运行期
        // getItemInSlot/consumeItem 会越界。此处按最大槽位向上取整到 9 的倍数(且 ≤54 背包上限)。
        int declared = (menu != null && menu.size > 0) ? menu.size : 27;
        int maxSlot = declared - 1;
        for (int s : inputSlots) maxSlot = Math.max(maxSlot, s);
        for (int s : outputSlots) maxSlot = Math.max(maxSlot, s);
        if (pslot >= 0) maxSlot = Math.max(maxSlot, pslot);
        for (int s : extra) maxSlot = Math.max(maxSlot, s);
        for (int s : placed) maxSlot = Math.max(maxSlot, s);
        int size = Math.min(54, Math.max(declared, ((maxSlot / 9) + 1) * 9));
        for (int i = 0; i < size; i++) {
            if (!functional.contains(i) && !placed.contains(i) && !extra.contains(i)) {
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
                    // 额外功能槽（如模板机器的模板槽）内容物也掉落，避免被吞
                    java.util.Set<Integer> extra = extraFunctionalSlots();
                    if (!extra.isEmpty()) {
                        int[] extraSlots = extra.stream().mapToInt(Integer::intValue).toArray();
                        inv.dropItems(b.getLocation(), extraSlots);
                    }
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
            op.addProgress(getSpeed());
            return;
        }
        WTRecipe r = active.remove(b.getLocation());
        if (r != null) {
            pushRecipeOutputs(b, inv, r);
        }
        inv.replaceExistingItem(pslot, progressBar);
        getMachineProcessor().endOperation(b);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        return matchRecipes(inv, recipes);
    }

    /**
     * 把已完成配方的产出推入机器（默认推入 outputSlots）。抽取为可覆盖钩子，供子类（如
     * {@link WTTemplateMachine} 的 {@code moreOutputIfMoreTemplates}）按需放大产出。
     */
    protected void pushRecipeOutputs(Block b, BlockMenu inv, WTRecipe r) {
        r.pushOutputs(inv, outputSlots);
    }

    /** 匹配结果：命中的配方 + 各输入项选中的输入槽下标（在 {@link #inputSlots} 中的位置）。消耗前保持有效。 */
    protected static final class Match {
        final WTRecipe recipe;
        final int[] chosen;
        Match(WTRecipe recipe, int[] chosen) { this.recipe = recipe; this.chosen = chosen; }
    }

    /** 在给定配方列表中匹配输入（供模板机器按当前模板筛选后复用）。仅匹配与校验，不消耗输入。 */
    protected Match findMatch(BlockMenu inv, List<WTRecipe> recipeList) {
        int[] slots = inputSlots;
        int slotCount = slots.length;
        ItemStack[] slotItems = new ItemStack[slotCount];
        java.util.Map<Integer, Integer> posOf = new java.util.HashMap<>();
        for (int s = 0; s < slotCount; s++) {
            posOf.put(slots[s], s);
            ItemStack it = inv.getItemInSlot(slots[s]);
            slotItems[s] = (it == null) ? null : ItemStackWrapper.wrap(it);
        }
        // 所有输入槽为空时不可能命中任何配方（注册配方至少含 1 个非空输入）：
        // 直接返回，避免空闲机器每 tick 白遍历全部配方（含昂贵的 isItemSimilar，高负载下显著省 TPS）。
        boolean anyInput = false;
        for (ItemStack si : slotItems) {
            if (si != null) { anyInput = true; break; }
        }
        if (!anyInput) return null;
        for (WTRecipe recipe : recipeList) {
            ItemStack[] inputs = recipe.getInput();
            int n = inputs.length;
            int[] chosen = new int[n];
            int matched = 0;
            boolean failed = false;
            for (int i = 0; i < n; i++) {
                chosen[i] = -1;
                ItemStack need = inputs[i];
                if (need == null) { matched++; continue; }
                int bound = recipe.inSlot(i);
                if (bound >= 0) {
                    // 绑定到指定槽：仅检查该槽
                    Integer pos = posOf.get(bound);
                    if (pos == null) { failed = true; break; }
                    ItemStack in = slotItems[pos];
                    if (in != null && in.getAmount() >= need.getAmount() && SlimefunUtils.isItemSimilar(in, need, true)) {
                        chosen[i] = pos;
                        matched++;
                    } else { failed = true; break; }
                } else {
                    for (int s = 0; s < slotCount; s++) {
                        ItemStack in = slotItems[s];
                        if (in != null && in.getAmount() >= need.getAmount() && SlimefunUtils.isItemSimilar(in, need, true)) {
                            chosen[i] = s;
                            matched++;
                            break;
                        }
                    }
                }
            }
            if (failed || matched != n) continue;
            int distinct = 0;
            for (int i = 0; i < n; i++) {
                boolean dup = false;
                for (int j = 0; j < i; j++) if (chosen[i] == chosen[j]) { dup = true; break; }
                if (!dup) distinct++;
            }
            if (distinct != n) continue;
            // 输出放不下时跳过本配方尝试下一个（而非整体放弃）：不同配方的输出项可能不同，
            // 某项输出放不下不应阻塞输出项不同的其它可合成配方。
            if (!InvUtils.fitAll(inv.toInventory(), recipe.getOutput(), outputSlots)) continue;
            // tick 可能异步执行：匹配用的是快照，消耗前对选中槽位的实时内容再校验，避免竞态吞错物品
            boolean stillValid = true;
            for (int i = 0; i < n; i++) {
                if (recipe.isNoConsume(i) || chosen[i] < 0) continue;
                ItemStack live = inv.getItemInSlot(slots[chosen[i]]);
                ItemStack need = inputs[i];
                if (live == null || live.getAmount() < need.getAmount()
                        || !SlimefunUtils.isItemSimilar(live, need, true)) {
                    stillValid = false;
                    break;
                }
            }
            if (!stillValid) continue;
            return new Match(recipe, chosen);
        }
        return null;
    }

    /** 用本机器的全部配方匹配（不消耗）。 */
    protected Match findMatch(BlockMenu inv) {
        return findMatch(inv, recipes);
    }

    /** 消耗已匹配配方的输入（跳过 noConsume 项与未占用槽位）。 */
    protected void consumeMatch(BlockMenu inv, Match m) {
        if (m == null) return;
        ItemStack[] inputs = m.recipe.getInput();
        for (int i = 0; i < inputs.length; i++) {
            if (!m.recipe.isNoConsume(i) && m.chosen[i] >= 0) {
                inv.consumeItem(inputSlots[m.chosen[i]], inputs[i].getAmount());
            }
        }
    }

    /** 匹配并消耗输入（tick 路径：操作会在机器内暂存，没电也不会丢输入）。 */
    protected MachineRecipe matchRecipes(BlockMenu inv, List<WTRecipe> recipeList) {
        Match m = findMatch(inv, recipeList);
        if (m == null) return null;
        consumeMatch(inv, m);
        return m.recipe;
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
