// 模拟生产端 WTRecipe 的匹配相关字段：inputs（含预解析的 needSfId）、绑定槽、noConsume。
// 与生产端 WTRecipe 的 inSlot/inputs/noConsume 一一对应。
package bench;

public final class Recipe {
    public final SimItem[] inputs;
    public final int[] inSlots;     // 每个输入绑定的槽位（-1=任意/自由槽），对应 WTRecipe.inSlot(i)
    public final boolean[] noConsume;
    public final String[] needSfId; // 每个输入预解析的 SF id（原版输入为 null），对应 WTRecipe.inputSfId(i)

    public Recipe(SimItem[] inputs, int[] inSlots, boolean[] noConsume) {
        this.inputs = inputs;
        this.inSlots = inSlots;
        this.noConsume = noConsume;
        // 模拟 WTRecipe 构造期对每个输入 SlimefunItem.getByItem(need).getId() 的预解析（仅一次，非每 tick）。
        this.needSfId = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            needSfId[i] = inputs[i] == null ? null : inputs[i].sfId;
        }
    }

    public int inSlot(int i) {
        return (inSlots != null && i < inSlots.length) ? inSlots[i] : -1;
    }

    public boolean isNoConsume(int i) {
        return i >= 0 && i < noConsume.length && noConsume[i];
    }

    public String needSfId(int i) {
        return (i >= 0 && i < needSfId.length) ? needSfId[i] : null;
    }
}
