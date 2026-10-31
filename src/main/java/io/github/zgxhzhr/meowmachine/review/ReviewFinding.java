package io.github.zgxhzhr.meowmachine.review;

import java.util.List;

/**
 * 一条审核发现。
 *
 * @param ruleId   命中的规则编号（1~35，见规则表）；{@code null} 表示**说不出具体依据**
 *                 —— 这种情况会触发「要不直接打回算了」那张梗图
 * @param quote    原文里出问题的片段（可为空）
 * @param reason   为什么有问题（给作者看的话）
 */
public record ReviewFinding(Integer ruleId, String quote, String reason) {

    /** 说不清依据的那种发现 */
    public static ReviewFinding unexplained(String reason) {
        return new ReviewFinding(null, null, reason);
    }

    /** 是否属于「说不出依据」 */
    public boolean isUnexplained() {
        return ruleId == null;
    }

    /**
     * 把一组发现压成一句话摘要（最多列前 3 条规则号）。
     */
    public static String summarizeRules(List<ReviewFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ReviewFinding f : findings) {
            if (f.isUnexplained()) {
                continue;
            }
            if (shown > 0) {
                sb.append("、");
            }
            sb.append("第 ").append(f.ruleId()).append(" 条");
            if (++shown >= 3) {
                break;
            }
        }
        return sb.toString();
    }
}
