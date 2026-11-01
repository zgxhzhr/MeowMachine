package io.github.zgxhzhr.meowmachine.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenAI 兼容的 Chat Completions 客户端。
 *
 * <p>只依赖 JDK 自带的 {@link HttpClient}，不引第三方库。
 *
 * <p>**所有调用都是异步的**，并且**超时即放弃**——审核流程绝不能因为 AI 慢而卡住，
 * 调用方拿到失败后应退回纯本地规则。
 */
public final class LlmClient {

    /**
     * 同时进行的 AI 请求上限。
     *
     * <p>线程池只有 2 条线程 + 无界队列时，50 个人同时投稿会排 8 分钟队，
     * 而「AI 绝不阻塞」的承诺就成了笑话。这里用信号量**快速失败**：
     * 拿不到许可直接按「AI 不可用」走兜底（本地规则/打回），不排队。
     */
    private static final java.util.concurrent.Semaphore SLOTS =
            new java.util.concurrent.Semaphore(6);

    /** 给调用方判断当前是否还有空位 */
    public static boolean tryAcquireSlot() {
        return SLOTS.tryAcquire();
    }

    public static void releaseSlot() {
        SLOTS.release();
    }

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单独的小线程池：不占主线程，也不和游戏逻辑共用 ForkJoinPool */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "meow-machine-ai");
        t.setDaemon(true);
        return t;
    });

    private LlmClient() {
    }

    /**
     * 发一次对话请求。
     *
     * @return 模型返回的文本内容；失败返回一个带异常的 future（调用方必须兜底）
     */
    public static CompletableFuture<String> chat(LlmConfig cfg, String systemPrompt, String userPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", cfg.model());
                body.addProperty("temperature", 0);

                JsonArray messages = new JsonArray();
                messages.add(message("system", systemPrompt));
                messages.add(message("user", userPrompt));
                body.add("messages", messages);

                // 要求返回 JSON 对象（OpenAI 兼容字段；不支持的网关会忽略它，不影响流程）
                JsonObject format = new JsonObject();
                format.addProperty("type", "json_object");
                body.add("response_format", format);

                String url = cfg.endpoint().replaceAll("/+$", "") + "/chat/completions";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSeconds())))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + cfg.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + resp.statusCode() + " " + head(resp.body()));
                }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new IllegalStateException("响应里没有 choices");
                }
                JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                return msg.get("content").getAsString();
            } catch (Exception e) {
                MeowMachine.LOG.warn("[喵呜机] AI 请求失败（将退回本地规则）: {}", e.toString());
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static String head(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
