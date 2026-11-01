package io.github.zgxhzhr.meowmachine.ai;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AI 配置门面：**以当前存档为准**。
 *
 * <p>旧版本把配置写在 {@code config/meow_machine_ai.json}（安装级），换存档会继承 key 与开关。
 * 现在改成 {@code <存档>/data/meow_machine_ai.dat}，只有一处例外：存档里还没有 key 时，
 * 把旧全局配置**导入一次**，这样升级后不用重新填。
 */
public final class AiConfigStore {

    private static volatile AiConfigSavedData store = null;
    private static volatile LlmConfig cache = null;

    private AiConfigStore() {
    }

    public static void attach(MinecraftServer server) {
        store = AiConfigSavedData.get(server);
        cache = null;
        if (store == null) {
            return;
        }
        if (!store.hasKey() && Files.isRegularFile(globalConfigFile())) {
            // 只在旧全局配置**已存在**时迁移；不存在就不要顺手造一个（那会让新存档又「继承」到东西）
            LlmConfig global = LlmConfig.loadOrCreate(FMLPaths.CONFIGDIR.get());
            if (global.usable()) {
                store.set(global);
                store.logMigrated();
                // 迁移完就把全局 key 清掉：否则以后每开一个新存档都会「继承」到它
                LlmConfig.save(FMLPaths.CONFIGDIR.get(),
                        global.withAiConfig(global.endpoint(), "", global.model()));
                MeowMachine.LOG.info("[喵呜机] 已清空全局 AI key（改为按存档保存）");
            }
        }
        MeowMachine.LOG.info("[喵呜机] AI 配置已按存档加载（enabled={}, model={}）",
                store.toConfig().enabled(), store.toConfig().model());
    }

    public static void detach() {
        cache = null;
        store = null;
    }

    /** 当前存档的配置（存档未加载时退回默认值，不会去读全局文件） */
    public static LlmConfig get() {
        LlmConfig c = cache;
        if (c != null) {
            return c;
        }
        AiConfigSavedData s = store;
        c = s == null ? LlmConfig.defaults() : s.toConfig();
        cache = c;
        return c;
    }

    /** 保存到当前存档 */
    public static void save(LlmConfig cfg) {
        AiConfigSavedData s = store;
        if (s != null) {
            s.set(cfg);
        }
        cache = cfg;
    }

    /** 兼容旧代码里的「按路径读」调用点：统一指向当前存档（没有存档时退回默认值） */
    @Deprecated
    public static LlmConfig cached(Path ignored) {
        return get();
    }

    /** 旧配置路径（只读，供迁移/诊断用） */
    public static Path globalConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("meow_machine_ai.json");
    }

    /** 全局配置文件是否存在（诊断用） */
    public static boolean hasGlobalConfig() {
        return Files.isRegularFile(globalConfigFile());
    }
}
