package io.github.zgxhzhr.meowmachine.review;

import java.util.List;

/**
 * 一次审核的完整结果。
 *
 * @param verdict   结论（PENDING 表示还在等人工）
 * @param findings  发现列表（可能为空 = 没查出错）
 * @param reviewer  审核者标识（玩家名 / "ai" / "local-rules" / "pending-human" / "fallback"）
 * @param stickerId 要展示的梗图 id，可为空
 */
public record ReviewResult(Verdict verdict, List<ReviewFinding> findings,
                           String reviewer, String stickerId) {

    public static ReviewResult pass(String reviewer, String stickerId) {
        return new ReviewResult(Verdict.PASS, List.of(), reviewer, stickerId);
    }

    public static ReviewResult of(Verdict verdict, List<ReviewFinding> findings,
                                  String reviewer, String stickerId) {
        return new ReviewResult(verdict, findings == null ? List.of() : List.copyOf(findings),
                reviewer, stickerId);
    }
}
