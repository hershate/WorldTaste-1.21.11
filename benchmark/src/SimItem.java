// 模拟一个物品堆：type 对应 Bukkit Material（int 枚举），sfId 对应 Slimefun PDC 物品 id（null=原版）。
// 这是 benchmark 对生产端 ItemStack 的简化替身，仅用于量化匹配算法的成本（无 Bukkit/服务端依赖）。
package bench;

public final class SimItem {
    public final int type;       // Material 的 int 替身（SF 头颅统一用一个值，模拟 PLAYER_HEAD）
    public final String sfId;    // Slimefun id；原版物品为 null
    public int amount;

    public SimItem(int type, String sfId, int amount) {
        this.type = type;
        this.sfId = sfId;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return (sfId != null ? sfId : ("V#" + type)) + "x" + amount;
    }
}
