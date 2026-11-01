package io.github.zgxhzhr.meowmachine.ai;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * AI 配置存档：{@code <世界>/data/meow_machine_ai.dat}。
 *
 * <p>为什么不放全局 config：AI key、模型、启用状态属于「这个存档/这个服务器的审核策略」，
 * 放在安装级配置里会导致新存档直接继承旧存档的 key 和开关（实测反馈）。
 * 现在按存档独立；从旧全局配置迁移一次（仅当存档里还没有 key 时）。
 */
public final class AiConfigSavedData extends SavedData {

    public static final String DATA_NAME = "meow_machine_ai";

    private boolean enabled = false;
    private String endpoint = LlmConfig.DEFAULT_ENDPOINT;
    private String apiKey = "";
    private String model = LlmConfig.DEFAULT_MODEL;
    private int timeoutSeconds = 20;
    private boolean takeover = false;

    public static AiConfigSavedData get(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage()
                .computeIfAbsent(AiConfigSavedData::load, AiConfigSavedData::new, DATA_NAME);
    }

    public LlmConfig toConfig() {
        return new LlmConfig(enabled, endpoint, apiKey, model, timeoutSeconds, takeover);
    }

    public void set(LlmConfig cfg) {
        this.enabled = cfg.enabled();
        this.endpoint = cfg.endpoint() == null ? LlmConfig.DEFAULT_ENDPOINT : cfg.endpoint();
        this.apiKey = cfg.apiKey() == null ? "" : cfg.apiKey();
        this.model = cfg.model() == null ? LlmConfig.DEFAULT_MODEL : cfg.model();
        this.timeoutSeconds = cfg.timeoutSeconds();
        this.takeover = cfg.takeover();
        setDirty();
    }

    /** 存档里是否已经有可用的 key（迁移判断用） */
    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ---------------- 序列化 ----------------

    public static AiConfigSavedData load(CompoundTag tag) {
        AiConfigSavedData d = new AiConfigSavedData();
        d.enabled = tag.getBoolean("enabled");
        d.endpoint = tag.contains("endpoint") ? tag.getString("endpoint") : LlmConfig.DEFAULT_ENDPOINT;
        d.apiKey = tag.getString("apiKey");
        d.model = tag.contains("model") ? tag.getString("model") : LlmConfig.DEFAULT_MODEL;
        d.timeoutSeconds = tag.contains("timeoutSeconds") ? tag.getInt("timeoutSeconds") : 20;
        d.takeover = tag.getBoolean("takeover");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        tag.putString("endpoint", endpoint);
        tag.putString("apiKey", apiKey);
        tag.putString("model", model);
        tag.putInt("timeoutSeconds", timeoutSeconds);
        tag.putBoolean("takeover", takeover);
        return tag;
    }

    /** 写一条迁移日志（集中在这里免得调用方还要引 LOG） */
    public void logMigrated() {
        MeowMachine.LOG.info("[喵呜机] 已把全局 AI 配置迁移进当前存档");
    }
}
