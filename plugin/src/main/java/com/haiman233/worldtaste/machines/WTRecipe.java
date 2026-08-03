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

    public WTRecipe(int seconds, ItemStack[] input, ItemStack[] output, int[] chances, boolean chooseOne, boolean[] noConsume) {
        super(seconds, input, output);
        this.chances = chances;
        this.chooseOne = chooseOne;
        this.noConsume = noConsume;
    }

    public boolean isNoConsume(int index) {
        return index >= 0 && index < noConsume.length && noConsume[index];
    }

    /** 完成时滚动产出：每个输出独立过 chance；若 chooseOne 则在通过项中再均匀择一。 */
    public List<ItemStack> rollOutput() {
        ItemStack[] base = getOutput();
        List<ItemStack> passed = new ArrayList<>();
        for (int i = 0; i < base.length; i++) {
            int ch = i < chances.length ? chances[i] : 100;
            if (ch >= 100 || (ch > 0 && ThreadLocalRandom.current().nextInt(100) < ch)) {
                passed.add(base[i]);
            }
        }
        if (chooseOne && !passed.isEmpty()) {
            ItemStack picked = passed.get(ThreadLocalRandom.current().nextInt(passed.size()));
            passed.clear();
            passed.add(picked);
        }
        return passed;
    }
}
