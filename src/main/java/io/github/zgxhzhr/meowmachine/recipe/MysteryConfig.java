package io.github.zgxhzhr.meowmachine.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 神秘合成配置：{@code config/meow_machine_mystery.json}。
 *
 * <ul>
 *   <li>{@code moddedPercent}：模组物品占多少百分比（0~100，默认 25 = 四分之一是模组物品）</li>
 *   <li>{@code vanillaWeight}：**旧字段**，每 10 张里几张原版；只在没有 moddedPercent 时兼容读取</li>
 *   <li>{@code moddedEnabled}：是否允许模组物品；false 时全部原版</li>
 * </ul>
 *
 * <p>判定是「假随机」：每轮 10 张 = 原版块（权重张）+ 模组块（10-权重 张），
 * 原版先发、模组垫底。默认 9+1 时，抽满 9 次原版后第 10 次必定是模组物品；
 * 抽完一轮才重配，所以不会出现「连着几十次都抽不到模组物品」。
 */
public final class MysteryConfig {

    /** 模组物品百分比（默认 25） */
    private static volatile int moddedPercent = 25;
    private static volatile boolean moddedEnabled = true;
    private static volatile boolean loaded = false;

    private MysteryConfig() {
    }

    public static int moddedPercent() {
        load();
        return moddedPercent;
    }

    public static boolean moddedEnabled() {
        load();
        return moddedEnabled;
    }

    /** 重新读配置（改完文件执行 /reload 或重进世界生效） */
    public static synchronized void reload() {
        loaded = false;
        load();
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
                    def.addProperty("moddedPercent", 25);
                    def.addProperty("moddedEnabled", true);
                    Files.writeString(file, def.toString(), StandardCharsets.UTF_8);
                }
                JsonObject obj = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("moddedPercent")) {
                    moddedPercent = Math.max(0, Math.min(100, obj.get("moddedPercent").getAsInt()));
                } else if (obj.has("vanillaWeight")) {
                    // 旧配置兼容：10 张里 vw 张原版 → 模组占比
                    int vw = Math.max(0, Math.min(10, obj.get("vanillaWeight").getAsInt()));
                    moddedPercent = (10 - vw) * 10;
                }
                if (obj.has("moddedEnabled")) {
                    moddedEnabled = obj.get("moddedEnabled").getAsBoolean();
                }
            } catch (Exception e) {
                MeowMachine.LOG.warn("[喵呜机] 读取神秘合成配置失败，使用默认值: {}", e.toString());
            }
            loaded = true;
            MeowMachine.LOG.info("[喵呜机] 神秘合成配置：模组物品 {}%，moddedEnabled={}",
                    moddedPercent, moddedEnabled);
        }
    }
}
