package com.haiman233.worldtaste.load;

import com.haiman233.worldtaste.WT;
import com.haiman233.worldtaste.WorldTastePlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

/** 从 jar 资源加载 YAML。 */
public final class Yaml {

    private Yaml() {}

    public static YamlConfiguration loadResource(WorldTastePlugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                WT.log("资源缺失: " + name);
                return new YamlConfiguration();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            WT.log("读取 " + name + " 失败: " + e);
            return new YamlConfiguration();
        }
    }
}
