// R6 专项微基准：量化启动期「YAML 文件名缓存（parse-once）」的收益。
//
// 背景：Setup.preloadDisplays 对 10 个内容文件各解析一次，随后各 Loader(ItemsLoader/FoodsLoader/
//   MobDropsLoader/RecipeMachineLoader/MultiBlockLoader/TemplateLoader/WorkbenchLoader/GeoLoader)
//   对同一文件再解析一次。即 items.yml(2.5MB)、mb_machines.yml(1.8MB) 等共 10 个文件被解析两次。
//   R6 在 Yaml.loadResource 加文件名缓存，每文件单次加载只解析一次；加载后 clearCache 释放解析树。
//
// 方法论（诚实声明）：
//   - 本基准无 Bukkit/SnakeYAML 依赖。YAML 解析的主导代价 = 逐字符/逐行扫描 + 按键构建 Map 结构（对象分配），
//     其代价与文件大小成正比。parseFile() 以「逐行扫描 + 每行提取键并写入 HashMap」建模该代价结构
//     （真实读全字符 + 真实 String/Map 分配，不可被 JIT 常量折叠）。
//   - 主指标「解析次数/次加载」（20→10）与生产端 SnakeYAML 解析次数同构，可直接外推；
//     耗时为次指标。绝对启动耗时仍以真实服务端为准（本环境无服务端，既有约束）。
//   - 使用仓库根的【真实内容文件】（run.sh/bat 在 benchmark/ 内运行 → ../items.yml）；缺失时合成代表规模内容兜底。
package bench;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class LoadBench {

    /** 被双解析的 10 个内容文件（与 Setup.ITEM_FILES 一致）。 */
    static final String[] FILES = {
        "items.yml", "machines.yml", "foods.yml", "mob_drops.yml", "geo_resources.yml",
        "recipe_machines.yml", "mb_machines.yml", "linked_recipe_machines.yml",
        "template_machines.yml", "workbenches.yml"
    };

    static final String[] CONTENTS = new String[FILES.length];
    static final long TOTAL_BYTES;
    static boolean usedRealFiles = true;

    static {
        // 候选根：run.sh/bat 在 benchmark/ 内运行（仓库根=..）；也兼容从仓库根运行（.）。
        Path[] roots = { Paths.get(".."), Paths.get("."), Paths.get("../..") };
        long tb = 0;
        for (int i = 0; i < FILES.length; i++) {
            String content = null;
            for (Path root : roots) {
                Path p = root.resolve(FILES[i]);
                if (Files.isRegularFile(p)) {
                    try { content = Files.readString(p, StandardCharsets.UTF_8); } catch (Exception ignored) { }
                    if (content != null) break;
                }
            }
            if (content == null) { content = synth(FILES[i]); usedRealFiles = false; }
            CONTENTS[i] = content;
            tb += content.length();
        }
        TOTAL_BYTES = tb;
    }

    private LoadBench() {}

    /** 代表 SnakeYAML 主导代价：逐行扫描 + 每行提取键并写入 HashMap（真实读全字符 + 真实分配）。 */
    static int parseFile(String content, long[] sink) {
        Map<String, int[]> map = new HashMap<>();
        for (String line : content.split("\n", -1)) {
            int len = line.length();
            sink[0] += len;
            int s = 0;
            while (s < len && line.charAt(s) == ' ') s++;
            if (s >= len) continue;
            char ch = line.charAt(s);
            if (ch == '#' || ch == '-' || ch == '\r') continue;
            int colon = line.indexOf(':', s);
            if (colon > s) {
                String key = line.substring(s, colon);
                int[] c = map.get(key);
                if (c == null) map.put(key, new int[] { 1 });
                else c[0]++;
            }
        }
        return map.size();
    }

    /** 旧：preloadDisplays（10 解析）+ 各 Loader（10 解析）= 20 解析/次加载。返回解析次数。 */
    static int oldLoad(long[] sink) {
        int parses = 0;
        for (String c : CONTENTS) { parseFile(c, sink); parses++; }
        for (String c : CONTENTS) { parseFile(c, sink); parses++; }
        return parses;
    }

    /** 新：文件名缓存，首访解析、次访命中（模拟 Yaml.CACHE）。返回解析次数（10）。 */
    static int newLoad(long[] sink) {
        int parses = 0;
        Map<String, Integer> cache = new HashMap<>();
        // preloadDisplays：首访 → 解析并缓存
        for (int i = 0; i < FILES.length; i++) {
            Integer v = cache.get(FILES[i]);
            if (v == null) { v = parseFile(CONTENTS[i], sink); parses++; cache.put(FILES[i], v); }
        }
        // 各 Loader：缓存命中（0 解析）
        for (int i = 0; i < FILES.length; i++) sink[0] += cache.get(FILES[i]);
        return parses;
    }

    /** 缺真实文件时的代表规模兜底（字节量对齐真实文件，令基准仍可运行）。 */
    private static String synth(String name) {
        int size = switch (name) {
            case "items.yml" -> 2_500_000;
            case "mb_machines.yml" -> 1_800_000;
            case "linked_recipe_machines.yml" -> 148_000;
            case "recipe_machines.yml" -> 190_000;
            case "machines.yml" -> 66_000;
            case "foods.yml" -> 76_000;
            case "workbenches.yml" -> 58_000;
            case "template_machines.yml" -> 33_000;
            case "mob_drops.yml" -> 29_000;
            default -> 512;
        };
        StringBuilder sb = new StringBuilder(size);
        int id = 0;
        while (sb.length() < size) {
            sb.append("WT_ENTRY_").append(id++).append(":\n");
            sb.append("  item:\n    name: \"&fx\"\n    material_type: skull_hash\n    material: ")
              .append(hex64(id)).append("\n");
            sb.append("  recipe:\n    1:\n      material_type: slimefun\n      material: WT_X\n      amount: 1\n");
        }
        return sb.toString();
    }

    private static String hex64(int seed) {
        char[] c = new char[64];
        String h = "0123456789abcdef";
        for (int i = 0; i < 64; i++) c[i] = h.charAt((seed * 31 + i * 7) & 15);
        return new String(c);
    }
}
