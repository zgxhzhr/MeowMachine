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
 *   <li>{@code vanillaWeight}：每 10 张牌堆里放几张**原版**物品（0~10，默认 9 = 90%）</li>
 *   <li>{@code moddedEnabled}：是否允许模组物品；false 时全部原版</li>
 * </ul>
 *
 * <p>判定是「假随机」：每轮按权重配好 10 张再洗，抽完一轮才重配，
 * 所以不会连抽十几次全是模组物品，也不会连续重复。
 */
public final class MysteryConfig {

    private static volatile int vanillaWeight = 9;
    private static volatile boolean moddedEnabled = true;
    private static volatile boolean loaded = false;

    private MysteryConfig() {
    }

    public static int vanillaWeight() {
        load();
        return vanillaWeight;
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
                    def.addProperty("vanillaWeight", 9);
                    def.addProperty("moddedEnabled", true);
                    Files.writeString(file, def.toString(), StandardCharsets.UTF_8);
                }
                JsonObject obj = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("vanillaWeight")) {
                    vanillaWeight = Math.max(0, Math.min(10, obj.get("vanillaWeight").getAsInt()));
                }
                if (obj.has("moddedEnabled")) {
                    moddedEnabled = obj.get("moddedEnabled").getAsBoolean();
                }
            } catch (Exception e) {
                MeowMachine.LOG.warn("[喵呜机] 读取神秘合成配置失败，使用默认值: {}", e.toString());
            }
            loaded = true;
            MeowMachine.LOG.info("[喵呜机] 神秘合成配置：vanillaWeight={}/10, moddedEnabled={}",
                    vanillaWeight, moddedEnabled);
        }
    }
}
