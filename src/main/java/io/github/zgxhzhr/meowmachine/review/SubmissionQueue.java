package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 人工审核队列。
 *
 * <p>**第一版是纯内存**：服务端重启会清空。持久化（世界存档 SavedData）排在下一批——
 * 先让「审核员能看到待审、能裁决」这条链路跑通，再解决存盘。
 *
 * <p>用 {@link LinkedHashMap} 保持提交顺序（先来先审）。
 */
public final class SubmissionQueue {

    /** 队列项：稿件 + 提交时间 */
    public record Entry(Submission submission, long submittedAt) {
    }

    private static final Map<UUID, Entry> PENDING = new LinkedHashMap<>();

    private SubmissionQueue() {
    }

    /** 入队。重复入队（同 id）会覆盖，保持幂等 */
    public static void enqueue(Submission sub, long now) {
        if (sub == null) {
            return;
        }
        PENDING.put(sub.id(), new Entry(sub, now));
        MeowMachine.LOG.info("[喵呜机] 稿件入队 id={} author={} 当前待审 {} 份",
                sub.id(), sub.authorName(), PENDING.size());
    }

    /** 待审快照（按提交顺序） */
    public static List<Entry> snapshot() {
        return new ArrayList<>(PENDING.values());
    }

    public static int size() {
        return PENDING.size();
    }

    public static Optional<Entry> peek(UUID id) {
        return Optional.ofNullable(PENDING.get(id));
    }

    /** 取出并从队列移除（裁决后调用） */
    public static Optional<Entry> claim(UUID id) {
        return Optional.ofNullable(PENDING.remove(id));
    }

    /** 仅移除（例如作者撤回） */
    public static boolean drop(UUID id) {
        return PENDING.remove(id) != null;
    }

    /** 按作者查（作者想看自己有哪些在审） */
    public static List<Entry> byAuthor(UUID author) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : PENDING.values()) {
            if (e.submission().authorUuid().equals(author)) {
                out.add(e);
            }
        }
        return out;
    }
}
