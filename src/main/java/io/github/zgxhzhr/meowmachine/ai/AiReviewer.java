package io.github.zgxhzhr.meowmachine.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 用 AI 判**语义类**规则（本地规则判不了的那部分）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>绝不让 AI 卡住流程</b>：任何异常/超时/解析失败都返回空列表，
 *       调用方拿到空列表就当作「AI 没查出问题」，继续用本地规则的结果。</li>
 *   <li><b>只判语义</b>：格式类（标点、emoji、分割线）已经由 {@code LocalRules} 判过，
 *       不重复交给 AI，省 token 也避免两边结论打架。</li>
 *   <li><b>要求严格 JSON</b>，但仍做了宽容解析（剥离 markdown 代码块），
 *       因为不少网关会自作主张套一层 ```json。</li>
 * </ul>
 */
public final class AiReviewer {

    /** 语义类规则说明。编号与 mcmod 的公共编辑规范一致，便于审核意见引条号 */
    private static final String RULES = """
            规则（只判语义，格式问题不用管）：
            - 第 1 条 主观词句：出现「我觉得」「个人认为」「小编」「笔者认为」这类第一人称或主观判断。
            - 第 7 条 机翻/AIGC 痕迹：句子不通顺、翻译腔、模板化套话、明显由机器生成。
            - 第 18 条 内容不实或凑字数：灌水、重复、通篇没有实际信息。
            """;

    private static final String SYSTEM_PROMPT = """
            你是 Minecraft 百科站点的内容审核员。检查用户投稿是否违反下列规则。
            """ + RULES + """
            只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块：
            {"violations":[{"rule":<规则号>,"quote":"<原文片段>","reason":"<一句话原因>"}]}
            没有违规时输出：{"violations":[]}
            """;

    private AiReviewer() {
    }

    /**
     * 异步审核。
     *
     * @return 违规列表；**任何失败都返回空列表**（表示 AI 没查出问题，不代表审核通过）
     */
    public static CompletableFuture<List<ReviewFinding>> review(LlmConfig cfg, String title, String body) {
        if (!cfg.usable()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String user = "标题：" + (title == null ? "" : title)
                + "\n正文：\n" + (body == null ? "" : body);
        return LlmClient.chat(cfg, SYSTEM_PROMPT, user)
                .thenApply(AiReviewer::parse)
                .exceptionally(e -> {
                    MeowMachine.LOG.warn("[喵呜机] AI 审核失败，按「无发现」处理: {}", e.toString());
                    return List.of();
                });
    }

    /** 把模型返回的文本解析成发现列表；解析不了就返回空 */
    static List<ReviewFinding> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String json = stripFence(raw.trim());
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray violations = root.getAsJsonArray("violations");
            if (violations == null) {
                return List.of();
            }
            List<ReviewFinding> out = new ArrayList<>();
            for (JsonElement e : violations) {
                JsonObject v = e.getAsJsonObject();
                Integer rule = v.has("rule") ? v.get("rule").getAsInt() : null;
                String quote = v.has("quote") ? v.get("quote").getAsString() : null;
                String reason = v.has("reason") ? v.get("reason").getAsString() : "AI 认为有问题";
                out.add(new ReviewFinding(rule, quote, reason));
            }
            return out;
        } catch (RuntimeException ex) {
            MeowMachine.LOG.warn("[喵呜机] AI 返回内容不是可解析的 JSON，已忽略: {}", head(raw));
            return List.of();
        }
    }

    /** 去掉模型常爱加的 ```json 围栏 */
    private static String stripFence(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }

    private static String head(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
