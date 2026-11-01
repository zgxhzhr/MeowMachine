package io.github.zgxhzhr.meowmachine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 神秘合成配置：{@code config/meow_machine_mystery.json}。
 *
 * <pre>
 * {
 *   "moddedPercentMin": 5,      // 每轮牌堆模组物品占比下限（%）
 *   "moddedPercentMax": 25,     // 上限（%），每轮在此区间内随机
 *   "moddedEnabled": true,      // false = 只抽原版
 *   "blacklist": [],            // 永不出现：精确 id、命名空间通配 ns:*、或 *关键词*
 *   "whitelist": []             // 非空时只允许这些（规则同上）
 * }
 * </pre>
 *
 * <p>判定是「假随机」：每轮配 20 张牌，模组张数在区间内随机，抽完一轮才重配。
 */
public final class MysteryConfig {

    private static volatile int moddedMin = 5;
    private static volatile int moddedMax = 25;
    private static volatile boolean moddedEnabled = true;
    private static volatile List<String> blacklist = List.of();
    private static volatile List<String> whitelist = List.of();
    private static volatile boolean loaded = false;

    private MysteryConfig() {
    }

    public static int moddedMin() {
        load();
        return moddedMin;
    }

    public static int moddedMax() {
        load();
        return moddedMax;
    }

    public static boolean moddedEnabled() {
        load();
        return moddedEnabled;
    }

    public static List<String> blacklist() {
        load();
        return blacklist;
    }

    public static List<String> whitelist() {
        load();
        return whitelist;
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    /** 名单匹配：精确 id / ns:* / *关键词* */
    public static boolean matches(List<String> rules, String id) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String raw : rules) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String rule = raw.trim().toLowerCase(java.util.Locale.ROOT);
            String lower = id.toLowerCase(java.util.Locale.ROOT);
            if (rule.equals(lower)) {
                return true;
            }
            if (rule.endsWith(":*") && lower.startsWith(rule.substring(0, rule.length() - 1))) {
                return true;
            }
            if (rule.startsWith("*") && rule.endsWith("*") && rule.length() > 2
                    && lower.contains(rule.substring(1, rule.length() - 1))) {
                return true;
            }
            if (rule.startsWith("*") && lower.endsWith(rule.substring(1))) {
                return true;
            }
            if (rule.endsWith("*") && lower.startsWith(rule.substring(0, rule.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        synchronized (MysteryConfig.class) {
            if (loaded) {
                return;
            }
            Path file = FMLPaths.CONFIGDIR.get().resolve("meow_machine_mystery.json");
            try {
                if (!Files.exists(file)) {
                    Files.createDirectories(file.getParent());
                    JsonObject def = new JsonObject();
                    def.addProperty("moddedPercentMin", 5);
                    def.addProperty("moddedPercentMax", 25);
                    def.addProperty("moddedEnabled", true);
                    def.add("blacklist", new JsonArray());
                    def.add("whitelist", new JsonArray());
                    Files.writeString(file, def.toString(), StandardCharsets.UTF_8);
                }
                JsonObject obj = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                int legacy = obj.has("moddedPercent") ? obj.get("moddedPercent").getAsInt() : -1;
                if (obj.has("moddedPercentMax")) {
                    moddedMax = clamp(obj.get("moddedPercentMax").getAsInt());
                } else if (legacy >= 0) {
                    moddedMax = clamp(legacy);
                }
                if (obj.has("moddedPercentMin")) {
                    moddedMin = clamp(obj.get("moddedPercentMin").getAsInt());
                } else if (legacy >= 0) {
                    moddedMin = clamp(legacy);
                }
                if (moddedMin > moddedMax) {
                    int t = moddedMin;
                    moddedMin = moddedMax;
                    moddedMax = t;
                }
                if (obj.has("moddedEnabled")) {
                    moddedEnabled = obj.get("moddedEnabled").getAsBoolean();
                }
                blacklist = readList(obj, "blacklist");
                whitelist = readList(obj, "whitelist");
            } catch (Exception e) {
                MeowMachine.LOG.warn("[喵呜机] 读取神秘合成配置失败，使用默认值: {}", e.toString());
            }
            loaded = true;
            MeowMachine.LOG.info(
                    "[喵呜机] 神秘合成配置：模组物品 {}%~{}%，moddedEnabled={}，黑名单 {} 条 / 白名单 {} 条",
                    moddedMin, moddedMax, moddedEnabled, blacklist.size(), whitelist.size());
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static List<String> readList(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray(key)) {
            if (el.isJsonPrimitive()) {
                out.add(el.getAsString());
            }
        }
        return List.copyOf(out);
    }
}
