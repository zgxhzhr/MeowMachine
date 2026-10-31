package io.github.zgxhzhr.meowmachine.review;

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
}
