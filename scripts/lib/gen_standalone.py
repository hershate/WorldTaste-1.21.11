# -*- coding: utf-8 -*-
"""
WorldTaste 独立插件数据生成器
================================
从原 RSC 脚本(scripts/)中抽取「行为参数」，生成插件运行期读取的数据文件：
  - plugin/src/main/resources/data/consumables.yml  : 食物消耗脚本 -> WT_eatConsumable 参数
  - plugin/src/main/resources/data/crops.yml        : 作物脚本   -> WT_setupCrop 参数
  - plugin/src/main/resources/data/fishing.yml      : diaoyu.js  -> 鱼饵掉落表

原因：独立插件无 GraalVM JS 引擎，不能在运行期 eval 脚本；故在构建期把“数据”抽出，
“逻辑”由 Java 实现。仅抽取结构标准的脚本；无法解析的会打印并跳过（其行为由 Java 默认逻辑兜底）。

用法（项目根目录）:
    python scripts/lib/gen_standalone.py
"""
import os
import re
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]          # WorldTaste 根
SCRIPTS = ROOT / "scripts"
OUT = ROOT / "plugin" / "src" / "main" / "resources" / "data"
OUT.mkdir(parents=True, exist_ok=True)

# ---------- consumables ----------
def parse_consumable(text):
    """抽取食物脚本行为。优先匹配 lib 形式(WT_eatConsumable/WT_eatFood)，否则解析独立脚本的
    setter/药水调用，产出统一 opts。返回 dict 或 None。"""
    opts = {}
    # 1) lib: WT_eatConsumable(event, { ... })
    m = re.search(r"WT_eatConsumable\s*\(\s*event\s*,\s*\{([^}]*)\}", text, re.S)
    if m:
        for k, v in re.findall(r"(\w+)\s*:\s*(\"[^\"]*\"|true|false|-?[0-9]+(?:\.[0-9]+)?)", m.group(1)):
            opts[k] = _coerce(v)
        opts["kind"] = "use"
        return opts
    # 2) lib: WT_eatFood(event, a, b, c)
    m = re.search(r"WT_eatFood\s*\(\s*event\s*,\s*([0-9]+(?:\.[0-9]+)?)\s*,\s*([0-9]+(?:\.[0-9]+)?)\s*,\s*([0-9]+(?:\.[0-9]+)?)", text)
    if m:
        return {"kind": "eat", "food": _coerce(m.group(1)), "saturation": _coerce(m.group(2)), "exhaustion": _coerce(m.group(3))}
    # 3) 独立脚本：解析 setter 与药水
    if "function onUse" not in text and "function onEat" not in text:
        return None
    opts["kind"] = "use" if "function onUse" in text else "eat"
    m = re.search(r"setFoodLevel\(\s*\w+\.getFoodLevel\(\)\s*\+\s*([0-9]+)\s*\)", text)
    if m: opts["food"] = int(m.group(1))
    else:
        m = re.search(r"setFoodLevel\(\s*([0-9]+)\s*\)", text)
        if m: opts["foodSet"] = int(m.group(1))
    if "Math.random()" in text and "getFoodLevel" in text and "food" not in opts and "foodSet" not in opts:
        opts["randomFood"] = 12  # jiu: 1..12
    m = re.search(r"setSaturation\(\s*\w+\.getSaturation\(\)\s*\+\s*([0-9]+)\s*\)", text)
    if m: opts["saturation"] = int(m.group(1))
    else:
        m = re.search(r"setSaturation\(\s*([0-9]+)\s*\)", text)
        if m: opts["saturationSet"] = int(m.group(1))
    m = re.search(r"setExhaustion\(\s*\w+\.getExhaustion\(\)\s*-\s*([0-9]+(?:\.[0-9]+)?)\s*\)", text)
    if m: opts["exhaustion"] = _coerce(m.group(1))
    m = re.search(r"setRemainingAir\(\s*([0-9]+)\s*\)", text)
    if m: opts["remainingAir"] = int(m.group(1))
    m = re.search(r"setFreezeTicks\(\s*([0-9]+)\s*\)", text)
    if m: opts["freezeTicks"] = int(m.group(1))
    m = re.search(r"setSaturatedRegenRate\(\s*([0-9]+)\s*\)", text)
    if m: opts["satRegen"] = int(m.group(1))
    m = re.search(r"setUnsaturatedRegenRate\(\s*([0-9]+)\s*\)", text)
    if m: opts["unsatRegen"] = int(m.group(1))
    m = re.search(r"setStarvationRate\(\s*([0-9]+)\s*\)", text)
    if m: opts["starvation"] = int(m.group(1))
    m = re.search(r"setMaximumAir\(\s*([0-9]+)\s*\)", text)
    if m: opts["maxAir"] = int(m.group(1))
    potions = []
    for pm in re.finditer(r"(?:createPotionEffect|new\s+org\.bukkit\.potion\.PotionEffect)\s*\(\s*([^,]+?),\s*([0-9]+)\s*,\s*([0-9]+)", text):
        ptype = pm.group(1).strip().split(".")[-1]
        potions.append({"type": ptype, "duration": int(pm.group(2)), "amplifier": int(pm.group(3))})
    if potions:
        # 变量型 type（如 jiu 的 effectType）回退为脚本中首个 PotionEffectType.X
        real_types = re.findall(r"PotionEffectType\.(\w+)", text)
        for p in potions:
            if not p["type"].replace("_", "").isupper() and real_types:
                p["type"] = real_types[0]
        opts["potions"] = potions
    if "FLINT_AND_STEEL" in text: opts["offhandFlint"] = True
    if re.search(r"offHandItem\.setAmount", text): opts["consumeOffhand"] = True
    if "getFoodLevel() >= 20" in text or "getFoodLevel()>=20" in text: opts["requireHungry"] = True
    if len(opts) <= 1:  # 仅 kind
        return None
    return opts

def _coerce(v):
    if v.startswith('"'): return v.strip('"')
    if v == "true": return True
    if v == "false": return False
    return float(v) if "." in v else int(v)

def gen_consumables():
    out = {}
    for d in ["", "gandi", "hetun", "yurenjie"]:
        base = SCRIPTS if d == "" else (SCRIPTS / d)
        if not base.exists(): continue
        for f in sorted(base.glob("*.js")):
            if f.name in ("diaoyu.js",): continue
            text = f.read_text(encoding="utf-8")
            parsed = parse_consumable(text)
            if parsed:
                name = f.stem if d == "" else f"{d}/{f.stem}"
                out[name] = parsed
    _write_yaml(OUT / "consumables.yml", out)
    print(f"consumables: {len(out)}")

# ---------- crops ----------
SMALL_STEPS = [round(1/10,6), round(1/6,6), round(1/3,6), 0.5, round(2/3,6), round(5/6,6), 1.0, round(7/6,6)]

def parse_crop(text):
    m = re.search(r"WT_setupCrop\s*\(\s*\{(.*?)\}\s*\)\s*;", text, re.S)
    if not m: return None
    body = m.group(1)
    cfg = {}
    mid = re.search(r'id\s*:\s*"([^"]+)"', body)
    mat = re.search(r"material\s*:\s*Material\.(\w+)", body)
    maxage = re.search(r"maxAge\s*:\s*([0-9]+)", body)
    grow = re.search(r"growMs\s*:\s*([0-9]+)", body)
    if not (mid and mat and maxage and grow): return None
    cfg["material"] = mat.group(1)
    cfg["maxAge"] = int(maxage.group(1))
    cfg["growMs"] = int(grow.group(1))
    # stages
    if "WT_SMALL_STEPS" in body:
        cfg["stages"] = "small"
    else:
        sm = re.search(r"stages\s*:\s*(\[[^\]]*\])", body, re.S)
        cfg["stages"] = "custom" if sm else "small"
    # drops
    drops = []
    for d in re.finditer(r'\{id:\s*"([^"]+)"\s*,\s*chance:\s*([0-9.]+)\}', body):
        drops.append({"id": d.group(1), "chance": float(d.group(2))})
    weighted = []
    for d in re.finditer(r'\{id:\s*"([^"]+)"\s*,\s*weight:\s*([0-9.]+)\}', body):
        weighted.append({"id": d.group(1), "weight": float(d.group(2))})
    if weighted: cfg["weightedDrops"] = weighted
    elif drops: cfg["drops"] = drops
    else: return None
    cfg["cropId"] = mid.group(1)
    return cfg

def gen_crops():
    out = {}
    for sub in ["seed", "gandi", "yurenjie", "hetun"]:
        base = SCRIPTS / sub
        if not base.exists(): continue
        for f in sorted(base.rglob("*.js")):  # 递归，含 seed/new/*
            text = f.read_text(encoding="utf-8")
            if "WT_setupCrop" not in text: continue
            parsed = parse_crop(text)
            if parsed:
                rel = f.relative_to(SCRIPTS).with_suffix("").as_posix()
                out[rel] = parsed
    _write_yaml(OUT / "crops.yml", out)
    print(f"crops: {len(out)}")

# ---------- fishing ----------
def gen_fishing():
    f = SCRIPTS / "diaoyu.js"
    if not f.exists():
        print("fishing: diaoyu.js 缺失"); return
    text = f.read_text(encoding="utf-8")
    tables = {}
    for t in re.finditer(r"const\s+(\w+_DROPS)\s*=\s*\[(.*?)\];", text, re.S):
        name, body = t.group(1), t.group(2)
        items = []
        for d in re.finditer(r'itemId:\s*"([^"]+)"\s*,\s*weight:\s*([0-9]+)', body):
            items.append({"id": d.group(1), "weight": int(d.group(2))})
        tables[name] = items
    # bait -> table 映射
    mapping = re.findall(r'"([^"]+)"\s*:\s*(\w+)\s*,', text)
    baits = {}
    for bait, table in mapping:
        if table in tables:
            baits[bait] = tables[table]
    rod = re.search(r'rodId:\s*"([^"]+)"', text)
    out = {"rod": rod.group(1) if rod else "WT_BAIWEIDIAOGAN", "baits": baits}
    _write_yaml(OUT / "fishing.yml", out)
    total = sum(len(v) for v in baits.values())
    print(f"fishing: rod={out['rod']} baits={len(baits)} drops={total}")

# ---------- yaml writer (递归块式 YAML，键全部加引号，列表用块式) ----------
def _scalar(v):
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, (int, float)): return str(v)
    return '"' + str(v).replace('"', '\\"') + '"'

def _emit(data, indent):
    pad = "  " * indent
    lines = []
    if isinstance(data, dict):
        for k, v in data.items():
            key = '"' + str(k) + '"'
            if isinstance(v, dict):
                lines.append(f"{pad}{key}:")
                lines.extend(_emit(v, indent + 1))
            elif isinstance(v, list):
                lines.append(f"{pad}{key}:")
                for item in v:
                    if isinstance(item, dict):
                        inner = ", ".join(f'"{ik}": {_scalar(iv)}' for ik, iv in item.items())
                        lines.append(f"{pad}  - {{{inner}}}")
                    else:
                        lines.append(f"{pad}  - {_scalar(item)}")
            else:
                lines.append(f"{pad}{key}: {_scalar(v)}")
    return lines

def _write_yaml(path, data):
    text = "\n".join(_emit(data, 0)) + "\n"
    path.write_text(text, encoding="utf-8")

if __name__ == "__main__":
    gen_consumables()
    gen_crops()
    gen_fishing()
    print("done -> plugin/src/main/resources/data/")
