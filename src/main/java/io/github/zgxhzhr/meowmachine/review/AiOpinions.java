package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 意见门面：内存镜像 + 世界存档写穿。
 *
 * <p>用途：开 AI 审核但不接管时，AI 先跑一遍把发现存下来，人工审核员在详情页就能看到
 * 「AI 意见」，作为裁决参考（不直接生效）。
 */
public final class AiOpinions {

    private static final Map<UUID, List<String>> BY_SUBMISSION = new LinkedHashMap<>();
    private static volatile AiOpinionsSavedData store = null;

    private AiOpinions() {
    }

    public static void attach(MinecraftServer server) {
        store = AiOpinionsSavedData.get(server);
        BY_SUBMISSION.clear();
        if (store != null) {
            BY_SUBMISSION.putAll(store.entries());
        }
    }

    public static void detach() {
        BY_SUBMISSION.clear();
        store = null;
    }

    /** 记一批 AI 发现（同一条投稿覆盖旧记录） */
    public static void put(UUID submissionId, List<ReviewFinding> findings) {
        if (submissionId == null || findings == null || findings.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (ReviewFinding f : findings) {
            String rule = f.ruleId() == null ? "?" : String.valueOf(f.ruleId());
            String reason = f.reason() == null ? "" : f.reason();
            lines.add("第 " + rule + " 条：" + reason);
        }
        BY_SUBMISSION.put(submissionId, lines);
        if (store != null) {
            store.put(submissionId, lines);
        }
    }

    /**
     * 按一次 AI 结论记录意见：违规逐条记；**通过时也记一句**（AI 的附言或默认说明）。
     *
     * <p>以前「AI 没发现违规」就什么都不存，结果审核详情里 AI 意见永远是「无」，
     * 看起来像意见被吞了。现在通过也留痕；AI 不可用时不动已有记录（保留旧的）。
     */
    public static void putOutcome(UUID submissionId, List<ReviewFinding> findings, String note) {
        if (submissionId == null) {
            return;
        }
        if (findings != null && !findings.isEmpty()) {
            put(submissionId, findings);
            return;
        }
        String text = (note == null || note.isBlank()) ? "AI 未发现违规，允许通过" : "通过：" + note.trim();
        List<String> lines = new ArrayList<>();
        lines.add(text);
        BY_SUBMISSION.put(submissionId, lines);
        if (store != null) {
            store.put(submissionId, lines);
        }
    }

    public static List<String> of(UUID submissionId) {
        if (submissionId == null) {
            return List.of();
        }
        List<String> lines = BY_SUBMISSION.get(submissionId);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    public static void clear(UUID submissionId) {
        if (submissionId == null) {
            return;
        }
        if (BY_SUBMISSION.remove(submissionId) != null && store != null) {
            store.clear(submissionId);
        }
    }
}
