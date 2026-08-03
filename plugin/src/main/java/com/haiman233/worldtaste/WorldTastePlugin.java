package com.haiman233.worldtaste;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 尘世百味 WorldTaste —— 独立 Slimefun4.1 附属插件主类。
 *
 * <p>由原 RSC 脚本版改写而来：内容仍来自同一组 YAML（已打包进 jar），但加载逻辑与原 JS 脚本
 * 行为全部以原生 Java 实现，不再依赖 RykenSlimefunCustomizer 及其 GraalVM 脚本引擎。</p>
 */
public final class WorldTastePlugin extends JavaPlugin implements SlimefunAddon {

    private static WorldTastePlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("尘世百味 开始加载（独立版）...");
        // TODO: 内容加载入口（groups/recipe_types/items/foods/machines/...）与行为注册
        getLogger().info("尘世百味 加载成功");
    }

    @Override
    public void onDisable() {
        getLogger().info("尘世百味 已卸载");
    }

    public static WorldTastePlugin getInstance() {
        return instance;
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/haiman233/WorldTaste/issues";
    }
}
