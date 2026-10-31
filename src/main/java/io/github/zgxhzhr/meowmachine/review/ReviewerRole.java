package io.github.zgxhzhr.meowmachine.review;

/**
 * 审核角色。
 *
 * <p>两级的差别只在**能不能终审**：
 * <ul>
 *   <li>{@link #REVIEWER} 审核员：可以给出最终结论（通过 / 协改 / 打回）</li>
 *   <li>{@link #ASSISTANT} 审核助理：只能标记发现、写意见，**不能终审**</li>
 * </ul>
 * OP 不在这个枚举里——OP 天然拥有审核员权限（见 {@code ReviewerRoster#canFinalize}）。
 */
public enum ReviewerRole {
    REVIEWER,
    ASSISTANT;

    /** 能否给出最终结论 */
    public boolean canFinalize() {
        return this == REVIEWER;
    }
}
