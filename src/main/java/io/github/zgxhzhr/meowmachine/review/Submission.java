package io.github.zgxhzhr.meowmachine.review;

import java.util.UUID;

/**
 * 一份投稿。作者可编辑字段只有 {@link #title} 与 {@link #body}。
 *
 * <p>不可变记录：编辑时产生新实例（{@link #withContent}），这样审核中界面拿到的快照不会被中途改掉。
 *
 * @param id         投稿 id
 * @param authorUuid 作者
 * @param authorName 作者名（展示用）
 * @param title      标题
 * @param body       正文
 * @param state      当前状态
 * @param revision   第几版（每次编辑 +1）
 */
public record Submission(UUID id, UUID authorUuid, String authorName,
                         String title, String body, SubmissionState state, int revision) {

    public static Submission draft(UUID authorUuid, String authorName) {
        return new Submission(UUID.randomUUID(), authorUuid, authorName, "", "",
                SubmissionState.DRAFT, 0);
    }

    /** 改内容：版本号 +1，状态回到 DRAFT（作者改完要再提交） */
    public Submission withContent(String newTitle, String newBody) {
        return new Submission(id, authorUuid, authorName,
                newTitle == null ? "" : newTitle,
                newBody == null ? "" : newBody,
                SubmissionState.DRAFT, revision + 1);
    }

    /** 提交审核 */
    public Submission submit() {
        return new Submission(id, authorUuid, authorName, title, body,
                SubmissionState.SUBMITTED, revision);
    }

    /** 应用审核结论 */
    public Submission apply(ReviewResult result) {
        SubmissionState next = result.verdict() == Verdict.PASS
                ? SubmissionState.APPROVED : SubmissionState.RETURNED;
        return new Submission(id, authorUuid, authorName, title, body, next, revision);
    }

    /** 内容是否为空（空稿不允许提交） */
    public boolean isEmpty() {
        return title.isBlank() && body.isBlank();
    }
}
