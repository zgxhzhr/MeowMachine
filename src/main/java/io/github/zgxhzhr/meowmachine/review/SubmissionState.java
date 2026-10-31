package io.github.zgxhzhr.meowmachine.review;

/**
 * 一份投稿的生命周期。
 *
 * <p>流转：{@link #DRAFT}（作者还在写）→ {@link #SUBMITTED}（已提交待审）
 * → {@link #APPROVED} / {@link #RETURNED}。
 *
 * <p>{@link #RETURNED} 与 {@link #SUBMITTED} 都允许作者继续编辑再投，
 * 这是「协改」流程的基础。
 */
public enum SubmissionState {
    /** 草稿：作者本地编辑中，不进队列 */
    DRAFT,
    /** 已提交，等待审核 */
    SUBMITTED,
    /** 通过 */
    APPROVED,
    /** 打回：作者可修改后重新提交 */
    RETURNED;

    /** 作者能不能改这份稿件 */
    public boolean editableByAuthor() {
        return this == DRAFT || this == SUBMITTED || this == RETURNED;
    }
}
