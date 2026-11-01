package io.github.zgxhzhr.meowmachine.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AI 接入配置。存在 {@code config/meow_machine_ai.json}，**不写死在代码里**。
 *
 * <p>字段对应 OpenAI 兼容接口：{@code endpoint} 是到 {@code /v1} 为止的根，
 * 请求会拼成 {@code <endpoint>/chat/completions}。
 *
 * @param enabled        是否启用 AI（总开关，默认关：不配就不调用）
 * @param endpoint       例如 {@code https://api.openai.com/v1}
 * @param apiKey         Bearer token
 * @param model          模型名
 * @param timeoutSeconds 单次请求超时；超时按「AI 不可用」处理
 * @param takeover       是否「AI 接管所有审核」（跳过人工优先；0.1.0 里没人读它）
 */
public record LlmConfig(boolean enabled, String endpoint, String apiKey, String model,
                        int timeoutSeconds, boolean takeover) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "meow_machine_ai.json";

    /** 默认服务商：DeepSeek（OpenAI 兼容，直接是 /v1/chat/completions） */
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/v1";
    /** 默认模型：deepseek-flash（DeepSeek 的快速对话模型；推理型是 deepseek-reasoner） */
    public static final String DEFAULT_MODEL = "deepseek-flash";

    /**
     * 内存缓存：配置是低频变更的静态数据，没必要每份投稿都在服务端主线程读一次磁盘。
     * {@link #save(Path, LlmConfig)} 会顺手失效缓存。
     */
    private static volatile LlmConfig cached = null;

    /** 默认配置：AI 默认关（不填 key 就不调用），地址与模型预置 DeepSeek */
    public static LlmConfig defaults() {
        return new LlmConfig(false, DEFAULT_ENDPOINT, "", DEFAULT_MODEL, 20, false);
    }

    /** 取当前配置（走了内存缓存）。服务端主线程可安全调用。 */
    public static LlmConfig cached(Path configDir) {
        LlmConfig c = cached;
        if (c == null) {
            c = loadOrCreate(configDir);
            cached = c;
        }
        return c;
    }

    /** 让缓存失效（保存后、或想强制重读磁盘时） */
    public static void invalidate() {
        cached = null;
    }

    /**
     * 从配置目录读取；文件不存在则写一份默认的再返回默认值。
     *
     * <p>读取失败一律退回默认值并记日志 —— AI 是可选增强，不能因为配置坏了就拦住审核流程。
     */
    public static LlmConfig loadOrCreate(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file, GSON.toJson(defaults()), StandardCharsets.UTF_8);
                MeowMachine.LOG.info("[喵呜机] 已生成默认 AI 配置：{}", file);
                return defaults();
            }
            JsonObject o = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (o == null) {
                return defaults();
            }
            String endpoint = o.has("endpoint") ? o.get("endpoint").getAsString().trim() : "";
            String model = o.has("model") ? o.get("model").getAsString().trim() : "";
            // 旧配置（0.1.0 生成的空 endpoint/model）自动补成 DeepSeek 默认值：
            // 空值对我们没有意义，留空只会让「勾了 AI 却不生效」更难排查。
            if (endpoint.isBlank()) {
                endpoint = DEFAULT_ENDPOINT;
            }
            if (model.isBlank()) {
                model = DEFAULT_MODEL;
            }
            return new LlmConfig(
                    o.has("enabled") && o.get("enabled").getAsBoolean(),
                    endpoint,
                    o.has("apiKey") ? o.get("apiKey").getAsString() : "",
                    model,
                    o.has("timeoutSeconds") ? o.get("timeoutSeconds").getAsInt() : 20,
                    o.has("takeover") && o.get("takeover").getAsBoolean());
        } catch (IOException | RuntimeException e) {
            MeowMachine.LOG.warn("[喵呜机] 读取 AI 配置失败，按「未启用」处理: {}", e.toString());
            return defaults();
        }
    }

    /** 写回配置文件（界面上切换开关时用），并让内存缓存失效 */
    public static void save(Path configDir, LlmConfig cfg) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve(FILE_NAME), GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MeowMachine.LOG.warn("[喵呜机] 写 AI 配置失败: {}", e.toString());
        } finally {
            invalidate();
        }
    }

    /** 改 endpoint / apiKey / model（其余字段原样保留），供游戏内配置界面保存用 */
    public LlmConfig withAiConfig(String newEndpoint, String newApiKey, String newModel) {
        return new LlmConfig(enabled,
                newEndpoint == null ? "" : newEndpoint.trim(),
                newApiKey == null ? "" : newApiKey.trim(),
                newModel == null ? "" : newModel.trim(),
                timeoutSeconds, takeover);
    }

    /** 改 enabled / takeover 的小工具（其余字段原样保留） */
    public LlmConfig withSwitches(boolean newEnabled, boolean newTakeover) {
        return new LlmConfig(newEnabled, endpoint, apiKey, model, timeoutSeconds, newTakeover);
    }

    /** 配置是否可用（启用了且三项关键字段都填了） */
    public boolean usable() {
        return enabled && !endpoint.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }
}
