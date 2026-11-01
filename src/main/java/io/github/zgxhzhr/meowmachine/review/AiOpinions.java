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
