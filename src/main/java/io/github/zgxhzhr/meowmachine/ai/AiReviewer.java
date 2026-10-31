package io.github.zgxhzhr.meowmachine.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 用 AI 判**语义类**规则（本地规则判不了的那部分）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>绝不让 AI 卡住流程</b>：异常/超时/解析失败都在有限时间内结束，不会挂住审核。</li>
 *   <li><b>「AI 挂了」与「AI 没发现问题」必须分开</b>：这是 0.1.0 最严重的一个 bug——
 *       以前所有失败都被归一成空列表，调用方把它当成「没查出问题」，于是一次网络抖动
 *       就让投稿**自动通过**。现在用 {@link AiOutcome} 明确区分：{@code available=false}
 *       表示没拿到可信结论，调用方必须走兜底，不能当通过。</li>
 *   <li><b>只判语义</b>：格式类（标点、emoji、分割线、灌水长度）已由 {@code LocalRules} 判过，
 *       prompt 里不再重复，省 token 也避免两边结论打架。</li>
 *   <li><b>要求严格 JSON</b>，但仍做宽容解析（剥离 markdown 代码块），因为不少网关会套一层 ```json。</li>
 * </ul>
 */
public final class AiReviewer {

    /** 一次 AI 审核的结果：available=false 表示「没拿到可信结论」 */
    public record AiOutcome(boolean available, List<ReviewFinding> findings) {

        public static AiOutcome unavailable() {
            return new AiOutcome(false, List.of());
        }

        public static AiOutcome of(List<ReviewFinding> findings) {
            return new AiOutcome(true, List.copyOf(findings));
        }

        public boolean clean() {
            return available && findings.isEmpty();
        }
    }

    /**
     * 语义类规则说明。编号与 mcmod 的公共编辑规范一致，便于审核意见引条号。
     *
     * <p>**只列 prompt 真正覆盖的条款**；与 {@code LocalRules.AI_ONLY_RULES} 保持一致。
     * 早先这里只写 1/7/18 却声明覆盖 11 条，结果 8 条规则既没有本地实现也不会被 AI 判。
     * 第 18 条（灌水/凑字数）留在本地规则，不重复交给 AI。
     */
    private static final String RULES = """
            规则（只判语义，格式问题不用管）：
            - 第 1 条 主观词句：出现「我觉得」「个人认为」「小编」「笔者认为」这类第一人称或主观判断。
            - 第 6 条 机翻痕迹：明显由机器翻译（语序倒装、直译腔、专有名词乱译）。
            - 第 7 条 AIGC 痕迹：模板化套话、空泛排比、明显由生成式模型写出的段落。
            """;

    private static final String SYSTEM_PROMPT = """
            你是 Minecraft 百科站点的内容审核员。检查用户投稿是否违反下列规则。
            """ + RULES + """
            只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块：
            {"violations":[{"rule":<规则号>,"quote":"<原文片段>","reason":"<一句话原因>"}]}
            没有违规时输出：{"violations":[]}
            """;

    /** 单条 reason / quote 的长度上限：避免拼进网络包时超长（ReviewResultPacket 的字段是有界的） */
    private static final int MAX_REASON = 120;
    private static final int MAX_QUOTE = 60;
    /** 单次最多采纳多少条：反馈给玩家的摘要只取前 3 条，再多没有意义 */
    private static final int MAX_FINDINGS = 5;

    private AiReviewer() {
    }

    /**
     * 异步审核。
     *
     * @return {@link AiOutcome}；**失败时 available=false**（不代表通过，调用方要兜底）
     */
    public static CompletableFuture<AiOutcome> review(LlmConfig cfg, String title, String body) {
        if (!cfg.usable()) {
            return CompletableFuture.completedFuture(AiOutcome.unavailable());
        }
        String user = "标题：" + (title == null ? "" : title)
                + "\n正文：\n" + (body == null ? "" : body);
        return LlmClient.chat(cfg, SYSTEM_PROMPT, user)
                .thenApply(raw -> parse(raw).map(AiOutcome::of).orElseGet(() -> {
                    MeowMachine.LOG.warn("[喵呜机] AI 返回内容无法解析，按「AI 不可用」处理");
                    return AiOutcome.unavailable();
                }))
                .exceptionally(e -> {
                    // 异常/超时：明确标记为「不可用」，绝不能当成「没查出问题」
                    MeowMachine.LOG.warn("[喵呜机] AI 请求失败，按「AI 不可用」处理: {}", e.toString());
                    return AiOutcome.unavailable();
                });
    }

    /**
     * 把模型返回的文本解析成发现列表。
     *
     * @return 解析成功时返回（可能为空的）列表；**解析失败返回 empty**，由调用方区分「不可用」
     */
    static Optional<List<ReviewFinding>> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String json = stripFence(raw.trim());
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray violations = root.getAsJsonArray("violations");
            if (violations == null) {
                // 结构不对：可能被网关套了别的包装，不猜，按不可用处理
                return Optional.empty();
            }
            List<ReviewFinding> out = new ArrayList<>();
            for (JsonElement e : violations) {
                if (out.size() >= MAX_FINDINGS) {
                    break;
                }
                try {
                    JsonObject v = e.getAsJsonObject();
                    Integer rule = v.has("rule") ? v.get("rule").getAsInt() : null;
                    String quote = v.has("quote") ? clamp(v.get("quote").getAsString(), MAX_QUOTE) : null;
                    String reason = v.has("reason")
                            ? clamp(v.get("reason").getAsString(), MAX_REASON) : "AI 认为有问题";
                    out.add(new ReviewFinding(rule, quote, reason));
                } catch (RuntimeException itemError) {
                    // 单条坏数据跳过，不影响整体结论
                    MeowMachine.LOG.debug("[喵呜机] 忽略无法解析的 AI 条目: {}", e);
                }
            }
            return Optional.of(List.copyOf(out));
        } catch (RuntimeException ex) {
            MeowMachine.LOG.warn("[喵呜机] AI 返回内容不是可解析的 JSON: {}", head(raw));
            return Optional.empty();
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

    private static String clamp(String s, int max) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max);
    }

    private static String head(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
