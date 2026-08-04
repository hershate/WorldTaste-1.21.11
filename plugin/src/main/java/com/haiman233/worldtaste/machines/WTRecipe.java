package com.haiman233.worldtaste.machines;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import org.bukkit.inventory.ItemStack;

/**
 * 带概率的机器配方：每个输出有独立 chance，可选 chooseOne（多输出择一）。
 * 对应 RSC 的 CustomMachineRecipe。ticks = seconds * 2（MachineRecipe 约定）。
 */
public class WTRecipe extends MachineRecipe {

    private final int[] chances;
    private final boolean chooseOne;
    private final boolean[] noConsume;
    /** 每个输入绑定的菜单槽（-1=任意输入槽），用于 linked 机器。 */
    private final int[] inSlots;
    /** 每个输出绑定的菜单槽（-1=任意输出槽），用于 linked 机器。 */
    private final int[] outSlots;

    public WTRecipe(int seconds, ItemStack[] input, ItemStack[] output, int[] chances, boolean chooseOne, boolean[] noConsume) {
        this(seconds, input, output, chances, chooseOne, noConsume, new int[0], new int[0]);
    }

    public WTRecipe(int seconds, ItemStack[] input, ItemStack[] output, int[] chances, boolean chooseOne,
                    boolean[] noConsume, int[] inSlots, int[] outSlots) {
        super(seconds, input, output);
        this.chances = chances;
        this.chooseOne = chooseOne;
        this.noConsume = noConsume;
        this.inSlots = inSlots;
        this.outSlots = outSlots;
    }

    public int inSlot(int i) {
        return (inSlots != null && i < inSlots.length) ? inSlots[i] : -1;
    }

    public boolean isNoConsume(int index) {
        return index >= 0 && index < noConsume.length && noConsume[index];
    }

    /** 完成时滚动产出并把每个通过项推入其绑定槽（无绑定则推入 freeSlots）。 */
    public void pushOutputs(me.mrCookieSlime.Slimefun.api.inventory.BlockMenu inv, int[] freeSlots) {
        ItemStack[] base = getOutput();
        List<Integer> passed = new ArrayList<>();
        for (int i = 0; i < base.length; i++) {
            int ch = i < chances.length ? chances[i] : 100;
            if (ch >= 100 || (ch > 0 && ThreadLocalRandom.current().nextInt(100) < ch)) passed.add(i);
        }
        if (chooseOne && !passed.isEmpty()) {
            int pick = passed.get(ThreadLocalRandom.current().nextInt(passed.size()));
            passed.clear();
            passed.add(pick);
        }
        for (int i : passed) {
            ItemStack o = base[i];
            if (o == null) continue;
            int slot = (outSlots != null && i < outSlots.length) ? outSlots[i] : -1;
            // 绑定槽推入有剩余时回退到自由槽，仍有剩余则掉落在机器旁（对齐 AContainer 的溢出处理）
            ItemStack leftover = (slot >= 0) ? inv.pushItem(o.clone(), slot) : inv.pushItem(o.clone(), freeSlots);
            if (leftover != null && leftover.getType() != org.bukkit.Material.AIR) {
                ItemStack rest = inv.pushItem(leftover, freeSlots);
                if (rest != null && rest.getType() != org.bukkit.Material.AIR
                        && inv.getLocation().getWorld() != null) {
                    inv.getLocation().getWorld().dropItemNaturally(inv.getLocation(), rest);
                }
            }
        }
    }
}
